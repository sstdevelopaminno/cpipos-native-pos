package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.offline.DemoReceiptLine
import com.cpipos.pos.next.core.offline.DemoSaleReceipt
import com.cpipos.pos.next.core.pos.Satang
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LiveReconcileReport(
    val recoveredReceipts: List<DemoSaleReceipt>,
    val confirmedCount: Int,
    val cancelledCount: Int,
    val remaining: List<LiveSaleAttempt>,
    val notes: List<String>
)

class LiveSaleReconciler(
    private val client: WebPosClient,
    private val journal: LiveSaleJournal
) {
    suspend fun reconcile(session: WebPosSession): LiveReconcileReport {
        val recovered = mutableListOf<DemoSaleReceipt>()
        val notes = mutableListOf<String>()
        var confirmed = 0
        var cancelled = 0

        for (attempt in journal.unresolved(session)) {
            try {
                when (val result = reconcileOne(session, attempt)) {
                    is OneResult.Confirmed -> {
                        journal.confirmed(session, attempt.id)
                        result.receipt?.let(recovered::add)
                        confirmed += 1
                        notes += "ยืนยันบิล ${result.orderNo} จากเซิร์ฟเวอร์แล้ว"
                    }
                    is OneResult.Cancelled -> {
                        journal.resolvedCancelled(session, attempt.id)
                        cancelled += 1
                        notes += "บิล ${result.orderNo} ถูกยกเลิกบนเซิร์ฟเวอร์แล้ว"
                    }
                    is OneResult.Review -> {
                        runCatching { journal.review(session, attempt.id) }
                        notes += result.message
                    }
                }
            } catch (e: Exception) {
                runCatching { journal.review(session, attempt.id) }
                notes += "บิล ${attempt.orderNo ?: attempt.id.take(8)} ยังตรวจสอบไม่สำเร็จ: " +
                    (e.message ?: e.javaClass.simpleName).take(90)
            }
        }

        return LiveReconcileReport(
            recoveredReceipts = recovered,
            confirmedCount = confirmed,
            cancelledCount = cancelled,
            remaining = journal.unresolved(session),
            notes = notes
        )
    }

    private suspend fun reconcileOne(
        session: WebPosSession,
        attempt: LiveSaleAttempt
    ): OneResult {
        val lines = attempt.linesOrNull()
        val received = attempt.receivedOrNull()
        val expectedTotal = attempt.totalOrNull()

        if (attempt.orderId == null) {
            if (lines.isNullOrEmpty() || received == null || expectedTotal == null) {
                return OneResult.Review(
                    "บิลเก่ารหัส ${attempt.id.take(8)} ไม่มี snapshot สำหรับ replay อัตโนมัติ"
                )
            }
            val created = client.createTakeawayOrder(session, lines, attempt.id, expectedTotal)
            if (created.total != expectedTotal) {
                return OneResult.Review(
                    "ยอดของบิล ${created.orderNo} เปลี่ยนจาก snapshot กรุณาตรวจสอบใน POS Web"
                )
            }
            journal.orderCreated(session, attempt.id, created.id, created.orderNo)
            return settleKnownOrder(
                session = session,
                attempt = attempt.copy(orderId = created.id, orderNo = created.orderNo),
                lines = lines,
                received = received,
                expectedTotal = created.total,
                serverSnapshot = WebOrderSnapshot(
                    id = created.id,
                    orderNo = created.orderNo,
                    status = created.status,
                    shiftId = session.shiftId,
                    total = created.total,
                    paidTotal = Satang.ZERO,
                    createdAtMs = attempt.createdAtMs
                )
            )
        }

        val recovery = resolveServerState(session, attempt)
            ?: return OneResult.Review(
                "ยังหา Order ${attempt.orderNo ?: attempt.orderId} บนเซิร์ฟเวอร์ไม่ได้ ห้ามเปิดบิลซ้ำ"
            )
        return when (recovery.snapshot.status) {
            "completed" -> OneResult.Confirmed(
                recovery.snapshot.orderNo,
                recovery.receipt ?: localReceipt(session, attempt, recovery.snapshot)
            )
            "cancelled" -> OneResult.Cancelled(recovery.snapshot.orderNo)
            else -> {
                if (lines.isNullOrEmpty() || received == null || expectedTotal == null) {
                    OneResult.Review(
                        "พบ Order ${recovery.snapshot.orderNo} แต่บิลเก่าไม่มี snapshot สำหรับชำระต่อ"
                    )
                } else {
                    settleKnownOrder(
                        session, attempt, lines, received, expectedTotal, recovery.snapshot
                    )
                }
            }
        }
    }

    private suspend fun settleKnownOrder(
        session: WebPosSession,
        attempt: LiveSaleAttempt,
        lines: List<DemoReceiptLine>,
        received: Satang,
        expectedTotal: Satang,
        serverSnapshot: WebOrderSnapshot
    ): OneResult {
        if (serverSnapshot.total != expectedTotal) {
            return OneResult.Review(
                "ยอด Server ของ ${serverSnapshot.orderNo} ไม่ตรง snapshot กรุณาตรวจสอบก่อนรับเงินซ้ำ"
            )
        }
        if (serverSnapshot.shiftId != null && session.shiftId != null &&
            serverSnapshot.shiftId != session.shiftId
        ) {
            return OneResult.Review(
                "บิล ${serverSnapshot.orderNo} อยู่คนละกะ จึงไม่ชำระซ้ำอัตโนมัติ"
            )
        }

        return try {
            // The bank-transfer journal is prepared ONLY after a cashier explicitly
            // confirms checking funds. Reconcile reuses the same sale/payment IDs.
            val paid = if (attempt.paymentMethod == "bank_transfer") {
                client.payExistingTransfer(
                    session = session,
                    orderId = serverSnapshot.id,
                    orderNo = serverSnapshot.orderNo,
                    lines = lines,
                    saleId = attempt.id,
                    expectedTotal = expectedTotal
                )
            } else {
                client.payExistingCash(
                    session = session,
                    orderId = serverSnapshot.id,
                    orderNo = serverSnapshot.orderNo,
                    lines = lines,
                    received = received,
                    saleId = attempt.id,
                    expectedTotal = expectedTotal
                )
            }
            OneResult.Confirmed(serverSnapshot.orderNo, paid.receipt)
        } catch (e: WebPosApiException) {
            if (e.code == "order_already_paid") {
                val after = resolveServerState(
                    session,
                    attempt.copy(
                        orderId = serverSnapshot.id,
                        orderNo = serverSnapshot.orderNo
                    )
                )
                if (after?.snapshot?.status == "completed") {
                    OneResult.Confirmed(
                        after.snapshot.orderNo,
                        after.receipt ?: localReceipt(session, attempt, after.snapshot)
                    )
                } else {
                    OneResult.Review(
                        "Server แจ้งว่าบิล ${serverSnapshot.orderNo} ชำระแล้ว แต่ยังอ่านหลักฐานยืนยันไม่ได้"
                    )
                }
            } else {
                throw e
            }
        }
    }

    private suspend fun resolveServerState(
        session: WebPosSession,
        attempt: LiveSaleAttempt
    ): WebOrderRecovery? {
        val orderId = attempt.orderId ?: return null
        try {
            return client.orderRecovery(session, orderId)
        } catch (e: WebPosApiException) {
            val compatibility404 = e.status == 404 &&
                (e.code == "non_json_response" || e.code == "http_404")
            if (!compatibility404) {
                if (e.code == "order_not_found") return null
                throw e
            }
        }

        val current = runCatching { client.currentShiftOrders() }
            .getOrNull()
            ?.firstOrNull { it.id == orderId }
        if (current != null) {
            return WebOrderRecovery(
                snapshot = current,
                receipt = if (current.status == "completed")
                    localReceipt(session, attempt, current) else null
            )
        }

        val orderNo = attempt.orderNo?.takeIf { it.isNotBlank() } ?: return null
        val day = LocalDate.ofInstant(
            Instant.ofEpochMilli(attempt.createdAtMs),
            ZoneId.of("Asia/Bangkok")
        )
        val history = try {
            client.receiptHistory(
                session = session,
                query = orderNo,
                from = day.minusDays(1),
                to = day.plusDays(1),
                status = "all",
                page = 1,
                pageSize = 10
            )
        } catch (_: Exception) {
            return null
        }
        val exact = history.records.firstOrNull {
            it.orderId == orderId || it.orderNo == orderNo
        } ?: return null
        return WebOrderRecovery(
            snapshot = WebOrderSnapshot(
                id = exact.orderId,
                orderNo = exact.orderNo,
                status = exact.status,
                shiftId = null,
                total = exact.receipt.total,
                paidTotal = if (exact.status == "completed") exact.receipt.total else Satang.ZERO,
                createdAtMs = exact.createdAtMs
            ),
            receipt = if (exact.status == "completed") exact.receipt else null
        )
    }

    private fun localReceipt(
        session: WebPosSession,
        attempt: LiveSaleAttempt,
        snapshot: WebOrderSnapshot
    ): DemoSaleReceipt {
        val received = attempt.receivedOrNull()
            ?: snapshot.paidTotal.takeIf { it > Satang.ZERO }
            ?: snapshot.total
        val change = if (received >= snapshot.total) received - snapshot.total else Satang.ZERO
        return DemoSaleReceipt(
            id = snapshot.id,
            billNo = snapshot.orderNo,
            createdAtMs = snapshot.createdAtMs,
            branchName = session.branchName,
            counterCode = session.deviceCode,
            modeLabel = "กลับบ้าน",
            lines = attempt.linesOrNull().orEmpty(),
            total = snapshot.total,
            received = received,
            change = change,
            isDemo = false,
            storeName = session.storeName,
            cashierName = session.cashierName,
            shiftLabel = null,
            paymentMethod = attempt.paymentMethod
        )
    }

    private sealed interface OneResult {
        data class Confirmed(
            val orderNo: String,
            val receipt: DemoSaleReceipt?
        ) : OneResult
        data class Cancelled(val orderNo: String) : OneResult
        data class Review(val message: String) : OneResult
    }
}
