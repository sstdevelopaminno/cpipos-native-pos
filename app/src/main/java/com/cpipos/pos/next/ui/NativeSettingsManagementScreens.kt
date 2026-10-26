package com.cpipos.pos.next.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val managementInk = Color(0xFF172B4E)
private val managementMuted = Color(0xFF71849B)
private val managementBlue = Color(0xFF176DED)

@Composable
private fun SettingsManagementShell(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onTab: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ ตั้งค่า") }
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = managementInk)
                        Text(subtitle, fontSize = 11.sp, color = managementMuted)
                    }
                }
                content()
            }
            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "settings",
                onMenuClick = onTab
            )
        }
    }
}

@Composable
private fun SettingsNotice(message: String?, error: Boolean = false) {
    if (message.isNullOrBlank()) return
    Surface(
        color = if (error) Color(0xFFFFEEF1) else Color(0xFFEAF8F0),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(11.dp),
            color = if (error) Color(0xFFB33A49) else Color(0xFF178A50),
            fontSize = 11.sp
        )
    }
}

@Composable
internal fun NativeBranchSettingsScreen(
    client: WebPosClient, session: WebPosSession, onBack: () -> Unit, onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var rows by remember(session.id) { mutableStateOf<List<WebSettingsBranch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<WebSettingsBranch?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            try { rows = client.settingsBranches(session); error = null }
            catch (e: Exception) { error = "โหลดสาขาไม่สำเร็จ: " + ((e as? WebPosApiException)?.code ?: e.message.orEmpty()) }
            finally { loading = false }
        }
    }
    LaunchedEffect(session.id) { reload() }

    SettingsManagementShell("เพิ่มสาขา", "ฟอร์มเดียวกับ CpIPOS · รหัสสาขา ชื่อสาขา ที่อยู่ และสถานะ", onBack, onTab) {
        Button(
            onClick = { edit = WebSettingsBranch("", "", "", "", true) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = managementBlue)
        ) { Text("+ เพิ่มสาขา", fontWeight = FontWeight.Bold) }
        SettingsNotice(notice)
        SettingsNotice(error, true)
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = managementBlue)
            }
        } else rows.forEach { row ->
            Surface(color = Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(row.name, fontWeight = FontWeight.ExtraBold, color = managementInk)
                            Text(row.code + " · " + if (row.active) "ใช้งาน" else "ปิดใช้งาน", color = managementMuted, fontSize = 10.sp)
                            if (row.address.isNotBlank()) Text(row.address, color = managementMuted, fontSize = 10.sp)
                        }
                        TextButton(onClick = { edit = row }) { Text("แก้ไข") }
                    }
                    if (row.id != session.branchId && row.active) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        client.deactivateSettingsBranch(session, row.id)
                                        notice = "ปิดใช้งานสาขา " + row.name + " แล้ว"
                                        reload()
                                    } catch (e: Exception) { error = "ปิดสาขาไม่สำเร็จ: " + ((e as? WebPosApiException)?.code ?: e.message.orEmpty()) }
                                    finally { busy = false }
                                }
                            }
                        ) { Text("ปิดใช้งานสาขา", color = Color(0xFFB33A49)) }
                    }
                }
            }
        }
    }

    edit?.let { original ->
        var code by remember(original.id) { mutableStateOf(original.code) }
        var name by remember(original.id) { mutableStateOf(original.name) }
        var address by remember(original.id) { mutableStateOf(original.address) }
        var active by remember(original.id) { mutableStateOf(original.active) }
        AlertDialog(
            onDismissRequest = { if (!busy) edit = null },
            title = { Text(if (original.id.isBlank()) "เพิ่มสาขา" else "แก้ไขสาขา", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(code, { code = it.uppercase().take(32) }, label = { Text("รหัสสาขา") }, singleLine = true)
                    OutlinedTextField(name, { name = it.take(120) }, label = { Text("ชื่อสาขา") }, singleLine = true)
                    OutlinedTextField(address, { address = it.take(500) }, label = { Text("ที่อยู่") }, minLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(active, { active = it }); Spacer(Modifier.width(8.dp)); Text("เปิดใช้งาน")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && code.isNotBlank() && name.isNotBlank(),
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                client.saveSettingsBranch(session, original.copy(code = code, name = name, address = address, active = active))
                                edit = null; notice = "บันทึกสาขาแล้ว"; reload()
                            } catch (e: Exception) { error = "บันทึกสาขาไม่สำเร็จ: " + ((e as? WebPosApiException)?.code ?: e.message.orEmpty()) }
                            finally { busy = false }
                        }
                    }
                ) { Text("บันทึก") }
            },
            dismissButton = { TextButton(onClick = { edit = null }, enabled = !busy) { Text("ยกเลิก") } }
        )
    }
}

@Composable
internal fun NativeDeviceSettingsScreen(
    client: WebPosClient, session: WebPosSession, onBack: () -> Unit, onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var devices by remember(session.id) { mutableStateOf<List<WebSettingsDevice>>(emptyList()) }
    var branches by remember(session.id) { mutableStateOf<List<WebSettingsBranch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<WebSettingsDevice?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            try {
                val pair = coroutineScope {
                    val a = async { client.settingsDevices(session) }
                    val b = async { client.settingsBranches(session) }
                    a.await() to b.await()
                }
                devices = pair.first; branches = pair.second; error = null
            } catch (e: Exception) { error = "โหลดเครื่องแคชเชียร์ไม่สำเร็จ: " + ((e as? WebPosApiException)?.code ?: e.message.orEmpty()) }
            finally { loading = false }
        }
    }
    LaunchedEffect(session.id) { reload() }

    SettingsManagementShell("เพิ่มเครื่องแคชเชียร์", "ฟอร์มเดียวกับ CpIPOS · สาขา รหัสเครื่อง ชื่อเครื่อง ประเภท สถานะ Counter และตำแหน่ง", onBack, onTab) {
        Button(
            onClick = { edit = WebSettingsDevice("", session.branchId, "", "", "pos_terminal", "active", true, "", "", null) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = managementBlue)
        ) { Text("+ เพิ่มเครื่องแคชเชียร์", fontWeight = FontWeight.Bold) }
        SettingsNotice(notice); SettingsNotice(error, true)
        if (loading) CircularProgressIndicator(color = managementBlue)
        devices.forEach { device ->
            val branchName = branches.firstOrNull { it.id == device.branchId }?.name ?: device.branchId
            Surface(color = Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(device.deviceName, fontWeight = FontWeight.ExtraBold, color = managementInk)
                    Text(device.deviceCode + " · " + branchName, fontSize = 10.sp, color = managementMuted)
                    Text(device.deviceType + " · " + device.status + " · " + if (device.locked) "ล็อกอินผูกเครื่อง" else "ไม่ล็อกเครื่อง", fontSize = 10.sp, color = managementMuted)
                    Row {
                        TextButton(onClick = { edit = device }) { Text("แก้ไข") }
                        TextButton(onClick = {
                            busy = true
                            scope.launch {
                                try { client.deleteSettingsDevice(session, device.id); notice = "ลบเครื่องแล้ว"; reload() }
                                catch (e: Exception) { error = "ลบเครื่องไม่สำเร็จ: " + ((e as? WebPosApiException)?.code ?: e.message.orEmpty()) }
                                finally { busy = false }
                            }
                        }) { Text("ลบ", color = Color(0xFFB33A49)) }
                    }
                }
            }
        }
    }

    edit?.let { original ->
        var branchId by remember(original.id) { mutableStateOf(original.branchId) }
        var code by remember(original.id) { mutableStateOf(original.deviceCode) }
        var name by remember(original.id) { mutableStateOf(original.deviceName) }
        var type by remember(original.id) { mutableStateOf(original.deviceType) }
        var status by remember(original.id) { mutableStateOf(original.status) }
        var locked by remember(original.id) { mutableStateOf(original.locked) }
        var counter by remember(original.id) { mutableStateOf(original.counterName) }
        var location by remember(original.id) { mutableStateOf(original.location) }
        var branchOpen by remember { mutableStateOf(false) }
        var typeOpen by remember { mutableStateOf(false) }
        var statusOpen by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) edit = null },
            title = { Text(if (original.id.isBlank()) "เพิ่มเครื่องแคชเชียร์" else "แก้ไขเครื่องแคชเชียร์") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { branchOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("สาขา: " + (branches.firstOrNull { it.id == branchId }?.name ?: "เลือกสาขา"))
                        }
                        DropdownMenu(branchOpen, { branchOpen = false }) {
                            branches.filter { it.active }.forEach { b ->
                                DropdownMenuItem({ Text(b.name + " (" + b.code + ")") }, { branchId = b.id; branchOpen = false })
                            }
                        }
                    }
                    OutlinedTextField(code, { code = it.take(40) }, label = { Text("รหัสเครื่อง") }, singleLine = true)
                    OutlinedTextField(name, { name = it.take(120) }, label = { Text("ชื่อเครื่อง") }, singleLine = true)
                    Box {
                        OutlinedButton(onClick = { typeOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("ประเภท: " + type) }
                        DropdownMenu(typeOpen, { typeOpen = false }) {
                            listOf("pos_terminal","mobile_scanner","kiosk").forEach { v -> DropdownMenuItem({ Text(v) }, { type=v; typeOpen=false }) }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { statusOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("สถานะ: " + status) }
                        DropdownMenu(statusOpen, { statusOpen = false }) {
                            listOf("active","inactive","maintenance").forEach { v -> DropdownMenuItem({ Text(v) }, { status=v; statusOpen=false }) }
                        }
                    }
                    OutlinedTextField(counter, { counter = it.take(120) }, label = { Text("ชื่อเคาน์เตอร์") }, singleLine = true)
                    OutlinedTextField(location, { location = it.take(160) }, label = { Text("ตำแหน่ง") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(locked, { locked=it }); Spacer(Modifier.width(8.dp)); Text("ล็อกอินผูกกับเครื่อง") }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && branchId.isNotBlank() && code.isNotBlank() && name.isNotBlank(),
                    onClick = {
                        busy=true
                        scope.launch {
                            try {
                                client.saveSettingsDevice(session, original.copy(branchId=branchId,deviceCode=code,deviceName=name,deviceType=type,status=status,locked=locked,counterName=counter,location=location))
                                edit=null; notice="บันทึกเครื่องแคชเชียร์แล้ว"; reload()
                            } catch(e:Exception){ error="บันทึกเครื่องไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty()) }
                            finally{ busy=false }
                        }
                    }
                ){ Text("บันทึก") }
            },
            dismissButton = { TextButton(onClick={edit=null},enabled=!busy){Text("ยกเลิก")} }
        )
    }
}

@Composable
internal fun NativePosUsersSettingsScreen(
    client: WebPosClient, session: WebPosSession, onBack: () -> Unit, onTab: (String) -> Unit
) {
    val scope=rememberCoroutineScope()
    var snapshot by remember(session.id){ mutableStateOf<WebPosUsersSnapshot?>(null) }
    var loading by remember{ mutableStateOf(true) }
    var busy by remember{ mutableStateOf(false) }
    var showAdd by remember{ mutableStateOf(false) }
    var editUser by remember{ mutableStateOf<WebPosUserRow?>(null) }
    var deleteUser by remember{ mutableStateOf<WebPosUserRow?>(null) }
    var error by remember{ mutableStateOf<String?>(null) }
    var notice by remember{ mutableStateOf<String?>(null) }
    fun reload(){ scope.launch{ loading=true; try{ snapshot=client.posUsers(session); error=null }catch(e:Exception){error="โหลดผู้ใช้งานไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{loading=false} } }
    LaunchedEffect(session.id){reload()}

    SettingsManagementShell("ผู้ใช้งาน POS","ฟอร์มเดียวกับ CpIPOS · ชื่อ อีเมล บทบาท รหัสพนักงาน ตำแหน่ง Permission Role, PIN และขอบเขตเครื่อง",onBack,onTab){
        if(snapshot?.canAdd==true) Button(onClick={showAdd=true},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=managementBlue)){Text("+ เพิ่มผู้ใช้งาน POS",fontWeight=FontWeight.Bold)}
        SettingsNotice(notice); SettingsNotice(error,true)
        if(loading) CircularProgressIndicator(color=managementBlue)
        snapshot?.items?.forEach{ user->
            Surface(
                color=Color.White,
                shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier=Modifier.fillMaxWidth().clickable(enabled=user.canEdit&&!busy){ editUser=user }
            ){
                Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(user.fullName.ifBlank{user.employeeCode},fontWeight=FontWeight.ExtraBold,color=managementInk)
                        Text(user.employeeCode+" · "+user.role+" · "+if(user.active)"ใช้งาน" else "ปิดใช้งาน",fontSize=10.sp,color=managementMuted)
                        if(user.email.isNotBlank()) Text(user.email,fontSize=10.sp,color=managementMuted)
                        Text("ขอบเขตเครื่อง: "+if(user.scopeMode=="all_devices")"ทุกเครื่อง" else "เครื่องเดียว",fontSize=10.sp,color=managementMuted)
                    }
                    if(user.canEdit) Switch(user.active,{ next->
                        if(!busy){busy=true;scope.launch{try{client.setPosUserActive(session,user.userId,next);notice="อัปเดตสถานะผู้ใช้แล้ว";reload()}catch(e:Exception){error="อัปเดตผู้ใช้ไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{busy=false}}}
                    })
                }
            }
        }
    }


    editUser?.let { original ->
        var fullName by remember(original.userId){ mutableStateOf(original.fullName) }
        var email by remember(original.userId){ mutableStateOf(original.email) }
        var role by remember(original.userId){ mutableStateOf(original.role) }
        var employeeCode by remember(original.userId){ mutableStateOf(original.employeeCode) }
        var position by remember(original.userId){ mutableStateOf(original.positionTitle) }
        var permissionRole by remember(original.userId){ mutableStateOf(original.permissionRole) }
        var scopeMode by remember(original.userId){ mutableStateOf(original.scopeMode) }
        var deviceId by remember(original.userId){ mutableStateOf(original.deviceId) }
        var active by remember(original.userId){ mutableStateOf(original.active) }
        var newPin by remember(original.userId){ mutableStateOf("") }
        var staffApproval by remember(original.userId){ mutableStateOf(original.canApproveCancelBill) }
        var staffPin by remember(original.userId){ mutableStateOf("") }
        var approvalPin by remember(original.userId){ mutableStateOf("") }
        var roleOpen by remember{ mutableStateOf(false) }
        var deviceOpen by remember{ mutableStateOf(false) }
        val validScope = scopeMode != "single_device" || deviceId != null

        AlertDialog(
            onDismissRequest={ if(!busy) editUser=null },
            title={ Text("แก้ไขผู้ใช้งาน POS",fontWeight=FontWeight.ExtraBold) },
            text={
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(fullName,{fullName=it.take(120)},label={Text("ชื่อ-นามสกุล")})
                    OutlinedTextField(email,{email=it.take(160)},label={Text("อีเมล")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
                    Box{
                        OutlinedButton(onClick={roleOpen=true},modifier=Modifier.fillMaxWidth()){Text("บทบาท: "+role)}
                        DropdownMenu(roleOpen,{roleOpen=false}){
                            val roleOptions=if(original.role=="owner") listOf("owner") else listOf("manager","staff","accountant","kitchen")
                            roleOptions.forEach{v->DropdownMenuItem({Text(v)},{role=v;roleOpen=false})}
                        }
                    }
                    OutlinedTextField(employeeCode,{employeeCode=it.filter(Char::isDigit).take(32)},label={Text("รหัสพนักงาน")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    OutlinedTextField(position,{position=it.take(120)},label={Text("ตำแหน่ง")})
                    OutlinedTextField(permissionRole,{permissionRole=it.take(80)},label={Text("Permission Role")})
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Switch(active,{active=it});Spacer(Modifier.width(8.dp));Text("เปิดใช้งานผู้ใช้")
                    }
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Switch(scopeMode=="single_device",{scopeMode=if(it)"single_device" else "all_devices"});Spacer(Modifier.width(8.dp));Text("จำกัดเครื่องเดียว")
                    }
                    if(scopeMode=="single_device"){
                        Box{
                            OutlinedButton(onClick={deviceOpen=true},modifier=Modifier.fillMaxWidth()){
                                Text("เครื่อง: "+(snapshot?.devices?.firstOrNull{it.id==deviceId}?.deviceName?:"เลือกเครื่อง"))
                            }
                            DropdownMenu(deviceOpen,{deviceOpen=false}){
                                snapshot?.devices?.filter{it.status=="active"}?.forEach{d->
                                    DropdownMenuItem({Text(d.deviceName+" ("+d.deviceCode+")")},{deviceId=d.id;deviceOpen=false})
                                }
                            }
                        }
                    }
                    if(role!="staff"){
                        OutlinedTextField(newPin,{newPin=it.filter(Char::isDigit).take(12)},label={Text("PIN ใหม่ (เว้นว่าง = ไม่เปลี่ยน)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                    } else {
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Switch(staffApproval,{staffApproval=it});Spacer(Modifier.width(8.dp));Text("อนุญาต PIN ยกเลิกบิล")
                        }
                        if(staffApproval&&!original.canApproveCancelBill){
                            OutlinedTextField(staffPin,{staffPin=it.filter(Char::isDigit).take(12)},label={Text("PIN พนักงาน 4-12 หลัก")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                        }
                    }
                    OutlinedTextField(approvalPin,{approvalPin=it.filter(Char::isDigit).take(12)},label={Text("PIN Owner/Manager สำหรับอนุมัติ")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                    if(original.canDelete){
                        TextButton(onClick={deleteUser=original;editUser=null},enabled=!busy){
                            Text("ลบผู้ใช้งานจากสาขานี้",color=Color(0xFFB33A49))
                        }
                    }
                }
            },
            confirmButton={
                Button(
                    enabled=!busy&&fullName.isNotBlank()&&employeeCode.isNotBlank()&&validScope,
                    onClick={
                        busy=true
                        scope.launch{
                            try{
                                client.updatePosUserProfile(session,original,fullName,email,role,employeeCode,position,permissionRole,approvalPin)
                                client.setPosUserDeviceScope(session,original.userId,scopeMode,deviceId)
                                if(active!=original.active) client.setPosUserActive(session,original.userId,active)
                                if(newPin.isNotBlank()) client.setPosUserPin(session,original.userId,newPin,approvalPin)
                                if(role=="staff"&&staffApproval!=original.canApproveCancelBill){
                                    client.setPosUserCancelBillApproval(session,original.userId,staffApproval,if(staffApproval)staffPin else "",approvalPin)
                                }
                                editUser=null;notice="บันทึกผู้ใช้งานแล้ว";reload()
                            }catch(e:Exception){
                                error="บันทึกผู้ใช้ไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())
                            }finally{busy=false}
                        }
                    }
                ){Text("บันทึก")}
            },
            dismissButton={TextButton(onClick={editUser=null},enabled=!busy){Text("ยกเลิก")}}
        )
    }

    deleteUser?.let { target ->
        AlertDialog(
            onDismissRequest={if(!busy)deleteUser=null},
            title={Text("ลบผู้ใช้งานจากสาขา",fontWeight=FontWeight.ExtraBold)},
            text={Text("ยืนยันลบ "+target.fullName.ifBlank{target.employeeCode}+" ออกจากสาขานี้?")},
            confirmButton={
                Button(
                    enabled=!busy,
                    colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB33A49)),
                    onClick={
                        busy=true
                        scope.launch{
                            try{
                                client.deletePosUser(session,target.userId)
                                deleteUser=null;notice="ลบผู้ใช้งานแล้ว";reload()
                            }catch(e:Exception){
                                error="ลบผู้ใช้ไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())
                            }finally{busy=false}
                        }
                    }
                ){Text("ยืนยันลบ")}
            },
            dismissButton={TextButton(onClick={deleteUser=null},enabled=!busy){Text("ยกเลิก")}}
        )
    }

    if(showAdd){
        var fullName by remember{mutableStateOf("")}; var email by remember{mutableStateOf("")}; var role by remember{mutableStateOf("staff")}
        var employeeCode by remember{mutableStateOf("")}; var position by remember{mutableStateOf("")}; var permissionRole by remember{mutableStateOf("staff")}
        var pin by remember{mutableStateOf("")}; var approvalPin by remember{mutableStateOf("")}; var scopeMode by remember{mutableStateOf("all_devices")}
        var deviceId by remember{mutableStateOf<String?>(null)}; var canCancel by remember{mutableStateOf(false)}
        var roleOpen by remember{mutableStateOf(false)}; var deviceOpen by remember{mutableStateOf(false)}
        val validScope = scopeMode != "single_device" || deviceId != null
        AlertDialog(
            onDismissRequest={if(!busy)showAdd=false},
            title={Text("เพิ่มผู้ใช้งาน POS",fontWeight=FontWeight.ExtraBold)},
            text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(fullName,{fullName=it.take(120)},label={Text("ชื่อ-นามสกุล")})
                OutlinedTextField(email,{email=it.take(160)},label={Text("อีเมล (เว้นว่างได้)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
                Box{OutlinedButton(onClick={roleOpen=true},modifier=Modifier.fillMaxWidth()){Text("บทบาท: "+role)};DropdownMenu(roleOpen,{roleOpen=false}){listOf("manager","staff","accountant","kitchen").forEach{v->DropdownMenuItem({Text(v)},{role=v;permissionRole=v;roleOpen=false})}}}
                OutlinedTextField(employeeCode,{employeeCode=it.filter(Char::isDigit).take(32)},label={Text("รหัสพนักงาน")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                OutlinedTextField(position,{position=it.take(120)},label={Text("ตำแหน่ง")})
                OutlinedTextField(permissionRole,{permissionRole=it.take(80)},label={Text("Permission Role")})
                OutlinedTextField(pin,{pin=it.filter(Char::isDigit).take(12)},label={Text("PIN ผู้ใช้งาน 4-12 หลัก")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
                Row(verticalAlignment=Alignment.CenterVertically){Switch(scopeMode=="single_device",{scopeMode=if(it)"single_device" else "all_devices"});Spacer(Modifier.width(8.dp));Text("จำกัดเครื่องเดียว")}
                if(scopeMode=="single_device") Box{OutlinedButton(onClick={deviceOpen=true},modifier=Modifier.fillMaxWidth()){Text("เครื่อง: "+(snapshot?.devices?.firstOrNull{it.id==deviceId}?.deviceName?:"เลือกเครื่อง"))};DropdownMenu(deviceOpen,{deviceOpen=false}){snapshot?.devices?.filter{it.status=="active"}?.forEach{d->DropdownMenuItem({Text(d.deviceName+" ("+d.deviceCode+")")},{deviceId=d.id;deviceOpen=false})}}}
                if(role=="staff") Row(verticalAlignment=Alignment.CenterVertically){Switch(canCancel,{canCancel=it});Spacer(Modifier.width(8.dp));Text("อนุญาต PIN ยกเลิกบิล")}
                OutlinedTextField(approvalPin,{approvalPin=it.filter(Char::isDigit).take(12)},label={Text("PIN Owner/Manager สำหรับอนุมัติ")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))
            }},
            confirmButton={Button(enabled=!busy&&fullName.isNotBlank()&&employeeCode.isNotBlank()&&validScope,onClick={
                busy=true;scope.launch{try{client.createPosUser(session,fullName,email,role,employeeCode,position,permissionRole,pin,scopeMode,deviceId,canCancel,approvalPin);showAdd=false;notice="เพิ่มผู้ใช้งานแล้ว";reload()}catch(e:Exception){error="เพิ่มผู้ใช้ไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{busy=false}}
            }){Text("เพิ่มผู้ใช้งาน")}},
            dismissButton={TextButton(onClick={showAdd=false},enabled=!busy){Text("ยกเลิก")}}
        )
    }
}

@Composable
internal fun NativeTableQrSettingsScreen(
    client: WebPosClient, session: WebPosSession, onBack: () -> Unit, onTab: (String) -> Unit
){
    val scope=rememberCoroutineScope()
    var floor by remember(session.id){mutableStateOf<WebTableFloor?>(null)}
    var selected by remember{mutableStateOf<WebDiningTable?>(null)}
    var policy by remember{mutableStateOf<WebTableQrPolicy?>(null)}
    var loading by remember{mutableStateOf(true)};var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)};var notice by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(session.id){try{floor=client.diningTables(session)}catch(e:Exception){error="โหลดโต๊ะไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{loading=false}}
    SettingsManagementShell("ตั้งค่า QR โต๊ะ","ใช้ policy เดียวกับ CpIPOS · ตามเวลา/ชั่วโมง หรือใช้งานจนปิดบิล",onBack,onTab){
        SettingsNotice(notice);SettingsNotice(error,true)
        if(loading) CircularProgressIndicator(color=managementBlue)
        floor?.tables?.forEach{t->Surface(color=Color.White,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().clickable{
            selected=t;busy=true;scope.launch{try{policy=client.tableQrPolicy(session,t.id);error=null}catch(e:Exception){error="โหลด QR policy ไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{busy=false}}
        }){Column(Modifier.padding(14.dp)){Text(t.code+" · "+t.name,fontWeight=FontWeight.ExtraBold,color=managementInk);Text("แตะเพื่อตั้งค่าอายุ QR",fontSize=10.sp,color=managementMuted)}}}
    }
    if(selected!=null&&policy!=null){
        val original=policy!!;var mode by remember(original.tableId){mutableStateOf(original.mode)};var ttl by remember(original.tableId){mutableStateOf((original.ttlMinutes?:1080).toString())}
        AlertDialog(onDismissRequest={if(!busy){selected=null;policy=null}},title={Text("QR โต๊ะ "+original.tableCode,fontWeight=FontWeight.ExtraBold)},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){RadioButton(mode=="time",{mode="time"});Text("ตามเวลา / ชั่วโมง")}
            if(mode=="time") OutlinedTextField(ttl,{ttl=it.filter(Char::isDigit).take(4)},label={Text("อายุ QR (นาที 15-1440)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            Row(verticalAlignment=Alignment.CenterVertically){RadioButton(mode=="bill",{mode="bill"});Text("ตามบิล · ใช้จนชำระ/ปิดบิล")}
            Text("เมื่อเปลี่ยน policy ระบบ CpIPOS จะ revoke QR เดิมของโต๊ะแล้วให้ออก QR ใหม่",fontSize=10.sp,color=managementMuted)
        }},confirmButton={Button(enabled=!busy&&(mode=="bill"||(ttl.toIntOrNull()?:0) in 15..1440),onClick={busy=true;scope.launch{try{client.saveTableQrPolicy(session,original.copy(mode=mode,ttlMinutes=if(mode=="bill")null else ttl.toInt()));notice="บันทึก QR โต๊ะแล้ว";selected=null;policy=null}catch(e:Exception){error="บันทึก QR ไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{busy=false}}}){Text("บันทึก")}},dismissButton={TextButton(onClick={selected=null;policy=null},enabled=!busy){Text("ยกเลิก")}})
    }
}

@Composable
internal fun NativeTaxSettingsScreen(
    client: WebPosClient, session: WebPosSession, onBack: () -> Unit, onTab: (String) -> Unit
){
    val scope=rememberCoroutineScope();var settings by remember(session.id){mutableStateOf<WebTaxSettings?>(null)}
    var loading by remember{mutableStateOf(true)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var notice by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(session.id){try{settings=client.taxSettings(session)}catch(e:Exception){error="โหลดภาษีไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{loading=false}}
    SettingsManagementShell("ตั้งค่าภาษี","ใช้ชุดเดียวกับ CpIPOS · VAT/ภาษีหัก ณ ที่จ่าย คำนวณจากยอดหลังส่วนลด",onBack,onTab){
        SettingsNotice(notice);SettingsNotice(error,true);if(loading) CircularProgressIndicator(color=managementBlue)
        settings?.let{current->
            Row(verticalAlignment=Alignment.CenterVertically){Switch(current.enabled,{settings=current.copy(enabled=it)});Spacer(Modifier.width(8.dp));Text("เปิดใช้งานภาษี",fontWeight=FontWeight.Bold)}
            current.lines.forEachIndexed{index,line->
                Surface(color=Color.White,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)){
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedTextField(line.label,{v->settings=current.copy(lines=current.lines.toMutableList().also{it[index]=line.copy(label=v.take(80))})},label={Text("ชื่อรายการภาษี")})
                        OutlinedTextField(line.ratePct.toString(),{v->val n=v.toDoubleOrNull();if(n!=null)settings=current.copy(lines=current.lines.toMutableList().also{it[index]=line.copy(ratePct=n.coerceIn(0.0,100.0))})},label={Text("อัตรา %")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                        var modeOpen by remember(line.id){ mutableStateOf(false) }
                        Box{
                            OutlinedButton(onClick={modeOpen=true},modifier=Modifier.fillMaxWidth()){
                                Text("รูปแบบ: "+if(line.mode=="deduct_from_bill")"หักออกจากบิล" else "บวกเพิ่มในบิล")
                            }
                            DropdownMenu(modeOpen,{modeOpen=false}){
                                DropdownMenuItem(
                                    {Text("บวกเพิ่มในบิล")},
                                    {
                                        settings=current.copy(lines=current.lines.toMutableList().also{
                                            it[index]=line.copy(mode="add_to_bill")
                                        })
                                        modeOpen=false
                                    }
                                )
                                DropdownMenuItem(
                                    {Text("หักออกจากบิล")},
                                    {
                                        settings=current.copy(lines=current.lines.toMutableList().also{
                                            it[index]=line.copy(mode="deduct_from_bill")
                                        })
                                        modeOpen=false
                                    }
                                )
                            }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically){Switch(line.active,{v->settings=current.copy(lines=current.lines.toMutableList().also{it[index]=line.copy(active=v)})});Spacer(Modifier.width(8.dp));Text("ใช้งานรายการนี้")}
                    }
                }
            }
            Button(onClick={busy=true;scope.launch{try{settings=client.saveTaxSettings(session,settings!!);notice="บันทึกภาษีแล้ว"}catch(e:Exception){error="บันทึกภาษีไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{busy=false}}},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=managementBlue)){Text("บันทึกตั้งค่าภาษี")}
        }
    }
}

@Composable
internal fun NativeLanguageSettingsScreen(
    session: WebPosSession, onBack: () -> Unit, onTab: (String) -> Unit
){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("cpipos_native_ui",Context.MODE_PRIVATE)}
    var language by remember{mutableStateOf(prefs.getString("language","th")?:"th")}
    SettingsManagementShell("เปลี่ยนภาษา","ตัวเลือกเดียวกับ CpIPOS · ไทย / English / ລາວ",onBack,onTab){
        listOf("th" to "ไทย","en" to "English","lo" to "ລາວ").forEach{(code,label)->
            Surface(color=Color.White,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().clickable{language=code;prefs.edit().putString("language",code).apply()}){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(language==code,{language=code;prefs.edit().putString("language",code).apply()});Text(label,fontWeight=FontWeight.Bold)}
            }
        }
        Text("บันทึกไว้ในเครื่อง POS นี้และใช้เป็นค่าภาษาของ Native UI ที่รองรับการแปล",fontSize=10.sp,color=managementMuted)
    }
}

@Composable
internal fun NativeOrderKitchenSettingsScreen(
    client: WebPosClient, session: WebPosSession, onBack: () -> Unit, onTab: (String) -> Unit
){
    val scope=rememberCoroutineScope();var policy by remember(session.id){mutableStateOf<WebOrderKitchenPolicy?>(null)}
    var loading by remember{mutableStateOf(true)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var notice by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(session.id){try{policy=client.orderKitchenPolicy(session)}catch(e:Exception){error="โหลดการแจ้งเตือนออเดอร์/ครัวไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{loading=false}}
    SettingsManagementShell("การแจ้งเตือนออเดอร์และครัว","ใช้ policy เดียวกับ CpIPOS และเคารพ IT override",onBack,onTab){
        SettingsNotice(notice);SettingsNotice(error,true);if(loading)CircularProgressIndicator(color=managementBlue)
        policy?.let{p->
            SettingSwitchCard("POP UP ออเดอร์จาก QR",p.popupEnabled,p.effectivePopupEnabled,p.popupForcedByIt,!busy){policy=p.copy(popupEnabled=it)}
            SettingSwitchCard("ส่งออเดอร์เข้าครัวอัตโนมัติ",p.kitchenAutoSendEnabled,p.effectiveKitchenAutoSendEnabled,p.sendForcedByIt,!busy){policy=p.copy(kitchenAutoSendEnabled=it)}
            SettingSwitchCard("พิมพ์ครัวอัตโนมัติ",p.kitchenAutoPrintEnabled,p.effectiveKitchenAutoPrintEnabled,p.printForcedByIt,!busy){policy=p.copy(kitchenAutoPrintEnabled=it)}
            Button(onClick={busy=true;scope.launch{try{policy=client.saveOrderKitchenPolicy(session,policy!!);notice="บันทึกการแจ้งเตือนออเดอร์และครัวแล้ว"}catch(e:Exception){error="บันทึกไม่สำเร็จ: "+((e as? WebPosApiException)?.code?:e.message.orEmpty())}finally{busy=false}}},enabled=!busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=managementBlue)){Text("บันทึก")}
        }
    }
}

@Composable
private fun SettingSwitchCard(title:String,storeValue:Boolean,effectiveValue:Boolean,forced:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){
    Surface(color=Color.White,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)){
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold,color=managementInk);Text((if(forced)"IT override · " else "")+"ค่าที่ใช้จริง: "+if(effectiveValue)"เปิด" else "ปิด",fontSize=10.sp,color=if(forced)Color(0xFF9B6518) else managementMuted)}
            Switch(storeValue,onChange,enabled=enabled&&!forced)
        }
    }
}

@Composable
internal fun NativeHelpCenterScreen(
    client: WebPosClient, session: WebPosSession, onBack: () -> Unit, onTab: (String) -> Unit
){
    val context=LocalContext.current
    fun open(url:String){runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
    SettingsManagementShell("ศูนย์ช่วยเหลือ","เชื่อมกับ CpIPOS Support Center และช่องทางบริษัท",onBack,onTab){
        Surface(color=Color.White,shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("ติดต่อสอบถาม / แจ้งปัญหา",fontSize=18.sp,fontWeight=FontWeight.ExtraBold,color=managementInk)
                Text("เปิดศูนย์ช่วยเหลือ CpIPOS เพื่อแจ้งปัญหาและพูดคุยกับ IT Support",fontSize=11.sp,color=managementMuted)
                Button(onClick={open(client.trustedWebUrl("/preview/pos/payments/support"))},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=managementBlue)){Text("เปิด CpIPOS Support Center")}
                OutlinedButton(onClick={open("https://lin.ee/f1LXpAF")},modifier=Modifier.fillMaxWidth()){Text("ติดต่อ LINE Support")}
                OutlinedButton(onClick={open("tel:0985460355")},modifier=Modifier.fillMaxWidth()){Text("โทร 098-546-0355")}
            }
        }
    }
}
