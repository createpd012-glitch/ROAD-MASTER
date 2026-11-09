package com.roadmaster.app

import android.os.Bundle
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.roadmaster.app.location.TrackingService
import com.roadmaster.app.ui.RoadMapView
import com.roadmaster.app.ui.TrackPoint

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF10243D)
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
    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Panel)) {
        Scaffold(containerColor = Navy, bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("지도") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.EmojiEvents, null) }, label = { Text("랭킹") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Route, null) }, label = { Text("기록") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("내 정보") })
            }
        }) { padding ->
            when (tab) {
                0 -> MapHome(Modifier.padding(padding), tracking, routePoints, { tracking = true }, { tracking = false })
                1 -> RankingScreen(Modifier.padding(padding))
                2 -> StatsScreen(Modifier.padding(padding))
                else -> ProfileScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable private fun MapHome(modifier: Modifier = Modifier, tracking: Boolean, routePoints: List<TrackPoint>, onTrackingStarted: () -> Unit, onTrackingStopped: () -> Unit) {
    val context = LocalContext.current
    val mapView = remember(context) { RoadMapView.create(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
            onTrackingStarted()
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
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ROAD MASTER", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("도로를 정복하라", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
            Surface(color = Color(0xFF17385B), shape = RoundedCornerShape(30.dp)) {
                Text(if (tracking) "● GPS 기록 중" else "● GPS 대기", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFF70B5FF), fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("이번 기록 GPS", "${routePoints.size}개", Modifier.weight(1f))
            StatCard("정확도 기준", "50m 이내", Modifier.weight(1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF122A3B), RoundedCornerShape(22.dp))) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { it.setRoute(routePoints.toList()) })
            Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), color = Color.White.copy(alpha = 0.92f), shape = RoundedCornerShape(8.dp)) {
                Text("지도 DEMO", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFF183047), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), color = Color.White.copy(alpha = 0.88f), shape = RoundedCornerShape(6.dp)) {
                Text(RoadMapView.OSM_ATTRIBUTION, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color.DarkGray, fontSize = 9.sp)
            }
            if (routePoints.isEmpty()) {
                Surface(Modifier.align(Alignment.Center), color = Color(0xDD10243D), shape = RoundedCornerShape(12.dp)) {
                    Text("GPS 기록을 시작하면 이동 경로가 표시됩니다", Modifier.padding(12.dp), color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Button(onClick = {
            if (tracking) {
                context.stopService(Intent(context, TrackingService::class.java))
                onTrackingStopped()
            } else {
                val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isEmpty()) {
                    ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
                    onTrackingStarted()
                } else permissionLauncher.launch(missing.toTypedArray())
            }
        }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(15.dp)) {
            Text(if (tracking) "GPS 기록 종료" else "GPS 이동 기록 시작", fontWeight = FontWeight.Bold)
        }
        Text("이동 중에는 화면을 조작하지 마세요. 현재 경로는 이 화면의 임시 표시이며 서버에 저장되지 않습니다.", color = Color(0xFF8FA5BC), fontSize = 10.sp)
    }
}

@Composable private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp)) { Text(title, color = Color(0xFF9EB3C9), fontSize = 12.sp); Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
}

@Composable private fun RankingScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("전국 랭킹", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text("이번 시즌 TOP 플레이어", color = Color(0xFF9EB3C9))
        listOf("1   ROAD KING        82,450 P", "2   BLUE RIDER        76,210 P", "3   MAP EXPLORER      69,880 P", "4   YOU               12,480 P").forEachIndexed { i, row ->
            Surface(color = if (i == 3) Color(0xFF153A60) else Panel, shape = RoundedCornerShape(14.dp)) { Text(row, Modifier.fillMaxWidth().padding(18.dp), color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
        Text("※ 현재는 화면 시안용 예시 데이터입니다. Firebase 연동 후 실제 사용자 랭킹으로 교체됩니다.", color = Color(0xFF9EB3C9), fontSize = 11.sp)
    }
}

@Composable private fun StatsScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("나의 이동 기록", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
        StatCard("총 이동 거리", "0.0 km", Modifier.fillMaxWidth())
        StatCard("방문 도로 구간", "0개", Modifier.fillMaxWidth())
        StatCard("반복 방문", "0회", Modifier.fillMaxWidth())
        StatCard("누적 점수", "0 P", Modifier.fillMaxWidth())
    }
}

@Composable private fun ProfileScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("플레이어 정보", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
        StatCard("닉네임", "ROAD PLAYER", Modifier.fillMaxWidth())
        Text("계정 연결, 개인정보 및 위치정보 동의, 알림 설정은 서비스 출시 전 연결됩니다.", color = Color(0xFF9EB3C9))
    }
}