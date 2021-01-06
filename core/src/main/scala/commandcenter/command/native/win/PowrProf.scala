package commandcenter.command.native.win

import com.sun.jna.Native

object PowrProf {
  Native.register("powrprof")

  @native def SetSuspendState(bHibernate: Boolean, bForce: Boolean, bWakeupEventsDisabled: Boolean): Boolean
}
