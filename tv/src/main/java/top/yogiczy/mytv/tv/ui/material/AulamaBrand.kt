package top.yogiczy.mytv.tv.ui.material

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme

private const val AULAMA_TV_LOGO_ASPECT_RATIO = 1444f / 605f

@Composable
fun AulamaBrandLogo(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.aulama_tv_logo),
        contentDescription = "Aulama TV",
        modifier = modifier.aspectRatio(AULAMA_TV_LOGO_ASPECT_RATIO),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun AulamaBrandSplash(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        AulamaBrandLogo(modifier = Modifier.width(360.dp))
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun AulamaBrandSplashPreview() {
    MyTVTheme { AulamaBrandSplash() }
}
