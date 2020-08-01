package commandcenter.util

object JavaVM {
  val isSubstrateVM: Boolean = System.getProperty("java.vm.name") == "Substrate VM"
}
