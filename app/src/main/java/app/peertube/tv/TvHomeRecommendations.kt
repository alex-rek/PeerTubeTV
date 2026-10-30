package app.peertube.tv

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat

/** Publishes the app's current video recommendations to the Android TV launcher. */
@SuppressLint("RestrictedApi") // AndroidX tvprovider marks launcher integration APIs as restricted despite providing them for apps.
object TvHomeRecommendations {
    private const val PREFS = "peertube"
    private const val CHANNEL_ID = "androidTvRecommendationsChannelId"
    private const val TAG = "TvHomeRecommendations"
    private const val CHANNEL_URI = "peertubetv://home"

    @Synchronized
    fun publish(context: Context, fallbackServer: String, videos: List<Video>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val chosen = videos.asSequence().filterNot { it.isLive == true }.distinctBy { "${it.sourceServer(fallbackServer)}|${it.uuid}" }.take(20).toList()
        if (chosen.isEmpty()) return
        runCatching {
            val channelId = getOrCreateChannel(context)
            val programsUri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
            val existing = mutableMapOf<String, Long>()
            context.contentResolver.query(programsUri, null, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(BaseColumns._ID)
                val providerIdColumn = cursor.getColumnIndex(TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID)
                if (idColumn >= 0 && providerIdColumn >= 0) {
                    while (cursor.moveToNext()) {
                        val providerId = cursor.getString(providerIdColumn)
                        if (!providerId.isNullOrBlank()) existing[providerId] = cursor.getLong(idColumn)
                    }
                }
            }

            val keep = mutableSetOf<String>()
            chosen.forEachIndexed { index, video ->
                val sourceServer = video.sourceServer(fallbackServer)
                val providerId = "${sourceServer.lowercase()}|${video.uuid}"
                keep += providerId
                val deepLink = Uri.Builder()
                    .scheme("peertubetv")
                    .authority("video")
                    .appendQueryParameter("server", sourceServer)
                    .appendQueryParameter("uuid", video.uuid)
                    .appendQueryParameter("title", video.name)
                    .appendQueryParameter("views", video.views.toString())
                    .appendQueryParameter("publishedAt", video.publishedAt.orEmpty())
                    .appendQueryParameter("channelName", video.channel?.name ?: video.account?.name.orEmpty())
                    .appendQueryParameter("channelHost", video.channel?.host ?: video.account?.host ?: video.host.orEmpty())
                    .appendQueryParameter("channelDisplayName", video.channel?.displayName ?: video.account?.displayName.orEmpty())
                    .build()
                val artwork = listOfNotNull(video.thumbnailUrl, video.thumbnailPath, video.previewPath)
                    .firstNotNullOfOrNull { ApiFactory.image(sourceServer, it) }
                val builder = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                    .setTitle(video.name.take(200))
                    .setDescription(video.channel?.displayName ?: video.account?.displayName ?: "PeerTube")
                    .setIntentUri(deepLink)
                    .setInternalProviderId(providerId)
                    .setWeight(chosen.size - index)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                artwork?.let { builder.setPosterArtUri(Uri.parse(it)) }
                val contentValues = builder.build().toContentValues()
                val existingId = existing[providerId]
                if (existingId != null) {
                    context.contentResolver.update(TvContractCompat.buildPreviewProgramUri(existingId), contentValues, null, null)
                } else {
                    context.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, contentValues)
                }
            }
            existing.filterKeys { it !in keep }.values.forEach { programId ->
                context.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(programId), null, null)
            }
        }.onFailure { Log.w(TAG, "Could not publish Android TV recommendations", it) }
    }

    @Synchronized
    private fun getOrCreateChannel(context: Context): Long {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedId = preferences.getLong(CHANNEL_ID, -1L)
        if (savedId >= 0L) {
            val exists = context.contentResolver.query(TvContractCompat.buildChannelUri(savedId), arrayOf(BaseColumns._ID), null, null, null)?.use { it.moveToFirst() } == true
            if (exists) return savedId
        }

        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(context.getString(R.string.tv_recommendations_channel))
            .setAppLinkIntentUri(Uri.parse(CHANNEL_URI))
            .build()
        val channelUri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: error("Android TV did not create a recommendations channel")
        val channelId = ContentUris.parseId(channelUri)
        preferences.edit().putLong(CHANNEL_ID, channelId).apply()
        runCatching { ChannelLogoUtils.storeChannelLogo(context, channelId, appLogo(context)) }
        runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
        return channelId
    }

    private fun appLogo(context: Context): Bitmap {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(20, 24, 30))
        val icon: Drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
        icon.setBounds(8, 8, 72, 72)
        icon.draw(canvas)
        return bitmap
    }
}
