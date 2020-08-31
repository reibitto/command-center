package commandcenter.util

object JavaVM {
  // Note: It's important for this to *not* be a val. We want to prevent Graal native-image from making this a build-time constant.
  lazy val isSubstrateVM: Boolean = System.getProperty("java.vm.name") == "Substrate VM"
}
