package commandcenter.view

import fansi.Str

object Renderer {

  def renderDefault(title: String, content: Str): Rendered.Ansi =
    Rendered.Ansi(fansi.Color.Blue(title) ++ fansi.Str(" ") ++ content)
}
