# Command Center Tools

For certain operations that we can't do due to certain limitations of the JVM (or Graal native-image), Command Center
uses a native executable for calling certain system tasks.

For example, say on macOS you want to bring the window to the front and activate it. This is not possible to do with
the JVM by itself. Instead, you can call `cc-tools activate pid` to do this for you.
