# Gson populates these data models reflectively from PeerTube/search responses and
# from SharedPreferences blobs. Keep their fields and constructors for minified builds.
-keep class app.peertube.tv.Video { *; }
-keep class app.peertube.tv.VideoPage { *; }
-keep class app.peertube.tv.PlaybackQueueItem { *; }
-keep class app.peertube.tv.LiveVideoSession { *; }
-keep class app.peertube.tv.CaptionPage { *; }
-keep class app.peertube.tv.VideoCaption { *; }
-keep class app.peertube.tv.CaptionLanguage { *; }
-keep class app.peertube.tv.Channel { *; }
-keep class app.peertube.tv.Account { *; }
-keep class app.peertube.tv.VideoFile { *; }
-keep class app.peertube.tv.Resolution { *; }
-keep class app.peertube.tv.StreamingPlaylist { *; }
-keep class app.peertube.tv.VideoState { *; }
-keep class app.peertube.tv.VideoCategory { *; }
-keep class app.peertube.tv.InstancePage { *; }
-keep class app.peertube.tv.PeerTubeChannelPage { *; }
-keep class app.peertube.tv.PeerTubeChannel { *; }
-keep class app.peertube.tv.PeerTubeInstance { *; }
-keep class app.peertube.tv.PeerTubeCustomConfig { *; }
-keep class app.peertube.tv.PeerTubeInstanceConfig { *; }
-keep class app.peertube.tv.PeerSeekPage { *; }
-keep class app.peertube.tv.PeerSeekVideo { *; }
-keep class app.peertube.tv.DiscoveredServer { *; }

-keepattributes Signature,*Annotation*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
