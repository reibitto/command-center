package commandcenter.view

import fansi.{Color, Str}

object Renderer {

  def renderDefault(title: String, content: Str): Rendered.Ansi =
    Rendered.Ansi(Color.Blue(title) ++ Str(" ") ++ content)
}
