# ShizukuStdinShell looks these up by name (reflection) for stdin-fed install sessions.
-keep class rikka.shizuku.Shizuku { *** newProcess(...); }
-keep class rikka.shizuku.ShizukuRemoteProcess { public boolean waitForTimeout(long, java.util.concurrent.TimeUnit); }
