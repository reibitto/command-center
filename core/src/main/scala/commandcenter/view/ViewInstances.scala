package commandcenter.view

import java.util.UUID

trait ViewInstances {
  implicit val viewString: View[String] = View(s => Rendered.Ansi(s))

  implicit val viewFansiStr: View[fansi.Str] = View(s => Rendered.Ansi(s))

  implicit val viewUUID: View[UUID] = View(u => Rendered.Ansi(u.toString))

  implicit val viewDefaultView: View[DefaultView] =
    View.ansi(d => fansi.Color.Blue(d.title) ++ fansi.Str(" ") ++ d.content)
}

object ViewInstances extends ViewInstances
