package commandcenter.util

import java.awt.datatransfer.{DataFlavor, SystemFlavorMap}
import scala.util.Try

object EnvironmentSetup {

  // This attempts to suppress the annoying IntelliJ data flavor errors in the logs:
  // https://stackoverflow.com/a/78788963/4094147
  def setup(): Unit =
    Try {
      val flavorMap = SystemFlavorMap.getDefaultFlavorMap.asInstanceOf[SystemFlavorMap]

      flavorMap.setFlavorsForNative(
        "JAVA_DATAFLAVOR:application/x-java-jvm-local-objectref; class=com.intellij.codeInsight.editorActions.FoldingData",
        Array(DataFlavor.getTextPlainUnicodeFlavor)
      )
    }

}
