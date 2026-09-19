# Optional/tooling references used by the Macrobenchmark stack.
# The measured app contains ProfileInstaller in its benchmark variant.
-dontwarn androidx.profileinstaller.ProfileInstallReceiver
-dontwarn androidx.startup.Initializer

# Compile-time annotations referenced by AndroidX Test.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
