package com.roadmaster.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.roadmaster.app.location.TrackingService
import com.roadmaster.app.ui.RoadMapView
import com.roadmaster.app.ui.TrackPoint

private val Navy = Color(0xFF071A35)
private val WoodDark = Color(0xFF123B68)
private val WoodMid = Color(0xFF23699B)
private val Gold = Color(0xFF75D9FF)
private val GoldLight = Color(0xFFE8FAFF)
private val Blue = Color(0xFF2389FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoadMasterApp() }
    }
}

@Composable
fun RoadMasterApp() {
    var tab by remember { mutableIntStateOf(0) }
    var tracking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("road_master_profile", Context.MODE_PRIVATE) }
    var musicEnabled by remember { mutableStateOf(preferences.getBoolean("background_music", true)) }
    DisposableEffect(musicEnabled) {
        if (musicEnabled) BackgroundMusicPlayer.start() else BackgroundMusicPlayer.stop()
        onDispose { BackgroundMusicPlayer.stop() }
    }
    val routePoints = remember { mutableStateListOf<TrackPoint>() }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != TrackingService.ACTION_LOCATION_UPDATE) return
                val lat = intent.getDoubleExtra(TrackingService.EXTRA_LATITUDE, Double.NaN)
                val lon = intent.getDoubleExtra(TrackingService.EXTRA_LONGITUDE, Double.NaN)
                val accuracy = intent.getFloatExtra(TrackingService.EXTRA_ACCURACY, 999f)
                val time = intent.getLongExtra(TrackingService.EXTRA_TIME, System.currentTimeMillis())
                if (lat.isFinite() && lon.isFinite() && accuracy <= 50f) {
                    routePoints.add(TrackPoint(lat, lon, accuracy, time))
                    if (routePoints.size > 1500) routePoints.removeAt(0)
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(TrackingService.ACTION_LOCATION_UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Navy, surface = WoodDark)) {
        when (tab) {
            0 -> MapHome(tracking, routePoints, { tracking = true }, { tracking = false }, { tab = it })
            1 -> RankingScreen(Modifier.fillMaxSize()) { tab = 0 }
            2 -> StatsScreen(Modifier.fillMaxSize()) { tab = 0 }
            else -> SettingsScreen(Modifier.fillMaxSize(), musicEnabled, { tab = 0 }) { enabled ->
                musicEnabled = enabled
                preferences.edit().putBoolean("background_music", enabled).apply()
            }
        }
    }
}

@Composable
private fun MapHome(
    tracking: Boolean,
    routePoints: MutableList<TrackPoint>,
    onTrackingStarted: () -> Unit,
    onTrackingStopped: () -> Unit,
    onTabSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember(context) { RoadMapView.create(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
            onTrackingStarted()
            Toast.makeText(context, "현재 위치를 확인하고 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(Modifier.fillMaxSize().background(Navy)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = {
                it.setRoute(routePoints.toList())
                routePoints.lastOrNull()?.let(it::setCurrentLocationMarker)
            }
        )

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    IcePanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                            Text("ROAD MASTER", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text("도로를 정복하라", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text(if (tracking) "● GPS 탐험 기록 중" else "● 탐험 준비 완료", color = if (tracking) Color(0xFF9EFFB0) else Gold, fontSize = 11.sp)
                        }
                    }
                }
                IceButton(
                    text = "내 위치",
                    icon = { Icon(Icons.Default.MyLocation, null, tint = GoldLight) },
                    onClick = {
                        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (!fine && !coarse) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        } else {
                            val latest = routePoints.lastOrNull()
                            if (latest != null) {
                                mapView.moveToCurrentLocation(latest)
                            } else {
                                LocationServices.getFusedLocationProviderClient(context).lastLocation
                                    .addOnSuccessListener { location ->
                                        if (location != null) {
                                            mapView.moveToCurrentLocation(TrackPoint(location.latitude, location.longitude, location.accuracy, location.time))
                                        } else {
                                            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
                                            onTrackingStarted()
                                            Toast.makeText(context, "현재 위치를 확인하고 있습니다.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, "위치를 가져오지 못했습니다. GPS 설정을 확인해 주세요.", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(color = Color(0xDDFFFFFF), shape = RoundedCornerShape(7.dp)) {
                        Text(RoadMapView.OSM_ATTRIBUTION, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = Color.DarkGray, fontSize = 9.sp)
                    }
                }
                IcePanel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BottomItem(if (tracking) "기록 종료" else "탐험 시작", Modifier.weight(1f), { Icon(Icons.Default.Explore, null, tint = GoldLight, modifier = Modifier.size(19.dp)) }, true) {
                            if (tracking) {
                                context.stopService(Intent(context, TrackingService::class.java))
                                onTrackingStopped()
                            } else {
                                val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                                if (missing.isEmpty()) {
                                    routePoints.clear()
                                    ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
                                    onTrackingStarted()
                                } else permissionLauncher.launch(missing.toTypedArray())
                            }
                        }
                        BottomItem("랭킹", Modifier.weight(1f), { Icon(Icons.Default.EmojiEvents, null, tint = GoldLight, modifier = Modifier.size(19.dp)) }) { onTabSelected(1) }
                        BottomItem("기록", Modifier.weight(1f), { Icon(Icons.Default.Route, null, tint = GoldLight, modifier = Modifier.size(19.dp)) }) { onTabSelected(2) }
                        BottomItem("설정", Modifier.weight(1f), { Icon(Icons.Default.Settings, null, tint = GoldLight, modifier = Modifier.size(19.dp)) }) { onTabSelected(3) }
                    }
                }
                Text("이동 중에는 화면을 조작하지 마세요.", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun IcePanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.shadow(10.dp, RoundedCornerShape(18.dp)), shape = RoundedCornerShape(18.dp), color = WoodDark,
        border = androidx.compose.foundation.BorderStroke(1.8.dp, Gold)) {
        Box(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF397FAE), Color(0xFF174A78), Color(0xFF0A2A4D))))) { content() }
    }
}

@Composable
private fun IceButton(text: String, modifier: Modifier = Modifier, highlighted: Boolean = false, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(modifier = modifier.heightIn(min = 46.dp).shadow(5.dp, RoundedCornerShape(13.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp), color = WoodMid, border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldLight)) {
        Row(Modifier.background(Brush.verticalGradient(if (highlighted) listOf(Color(0xFFE5FCFF), Color(0xFF6CD9F5), Color(0xFF2385B7)) else listOf(Color(0xFF62B8E3), Color(0xFF286E9F), Color(0xFF123E69)))).padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { icon(); Spacer(Modifier.width(6.dp)) }
            Text(text, color = if (highlighted) Color(0xFF07304D) else Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BottomItem(text: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit, selected: Boolean = false, onClick: () -> Unit) {
    Surface(modifier = modifier.height(58.dp).clickable(onClick = onClick), shape = RoundedCornerShape(11.dp),
        color = if (selected) Color(0xFF2D8EBB) else Color(0xFF123F69), border = androidx.compose.foundation.BorderStroke(1.dp, Gold)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            Spacer(Modifier.height(3.dp))
            Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = WoodDark, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Gold)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = GoldLight, fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun RankingScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().background(Navy).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("← 지도", color = Gold, fontSize = 14.sp, modifier = Modifier.clickable { onBack() })
        Text("전국 랭킹", color = GoldLight, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text("이번 시즌 TOP 플레이어", color = Color(0xFFCFB991))
        listOf("1   ROAD KING        82,450 P", "2   BLUE RIDER        76,210 P", "3   MAP EXPLORER      69,880 P", "4   YOU               12,480 P").forEachIndexed { i, row ->
            Surface(color = WoodDark, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.65f))) {
                Text(row, Modifier.fillMaxWidth().padding(18.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("※ 현재는 화면 시안용 예시 데이터입니다. Firebase 연동 후 실제 사용자 랭킹으로 교체됩니다.", color = Color(0xFFCFB991), fontSize = 11.sp)
    }
}

@Composable private fun StatsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().background(Navy).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("← 지도", color = Gold, fontSize = 14.sp, modifier = Modifier.clickable { onBack() })
        Text("나의 이동 기록", color = GoldLight, fontSize = 27.sp, fontWeight = FontWeight.Black)
        StatCard("총 이동 거리", "0.0 km", Modifier.fillMaxWidth())
        StatCard("방문 도로 구간", "0개", Modifier.fillMaxWidth())
        StatCard("반복 방문", "0회", Modifier.fillMaxWidth())
        StatCard("누적 점수", "0 P", Modifier.fillMaxWidth())
    }
}

@Composable private fun SettingsScreen(modifier: Modifier = Modifier, musicEnabled: Boolean, onBack: () -> Unit, onMusicChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("road_master_profile", Context.MODE_PRIVATE) }
    var nickname by remember { mutableStateOf(prefs.getString("nickname", "ROAD PLAYER") ?: "ROAD PLAYER") }
    var editNickname by remember { mutableStateOf(nickname) }
    Column(modifier.fillMaxSize().background(Navy).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("← 지도", color = Gold, fontSize = 14.sp, modifier = Modifier.clickable { onBack() })
        Text("설정", color = GoldLight, fontSize = 27.sp, fontWeight = FontWeight.Black)
        IcePanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, tint = Gold, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("배경음악", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("신나는 오리지널 게임 음악", color = GoldLight, fontSize = 12.sp)
                }
                Switch(checked = musicEnabled, onCheckedChange = onMusicChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = WoodDark, checkedTrackColor = Gold))
            }
        }
        IcePanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("플레이어 닉네임", color = GoldLight, fontSize = 13.sp)
                Text(nickname, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = editNickname, onValueChange = { if (it.length <= 16) editNickname = it }, label = { Text("새 닉네임") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                IceButton(text = "닉네임 저장", modifier = Modifier.fillMaxWidth(), highlighted = true, onClick = {
                    val value = editNickname.trim()
                    if (value.isNotEmpty()) {
                        nickname = value
                        prefs.edit().putString("nickname", value).apply()
                        Toast.makeText(context, "닉네임을 저장했습니다.", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "닉네임을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                })
            }
        }
        Text("음악은 앱에서 직접 생성하는 오리지널 사운드입니다.", color = GoldLight, fontSize = 11.sp)
    }
}
