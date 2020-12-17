# Plugins

## Installation

You can place external plugins in this directory and they'll be included in the classpath at startup. You can then refer to them by its full type name in `application.conf`. For example:

```
{type: "commandcenter.strokeorder.StrokeOrderCommand$"}
```

Note: The '$' is needed as it's referring to the `StrokeOrderCommand` object, not class.

## Developing your own plugin

Besides the installation and configuration step above, there is nothing different between external Commands and built-in Commands.

You can refer to the `extras` module for example external plugins, such as `StrokeOrderCommand`.

## Limitations

Currently external plugins don't work with Graal native-image due to limitations regarding reflection.
