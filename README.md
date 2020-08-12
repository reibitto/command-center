# Command Center

![Scala CI](https://github.com/reibitto/command-center/workflows/Scala%20CI/badge.svg)

*A CLI-based launcher and general productivity tool.*

## What is it?

Command Center is used to launch applications, find files, control music playback, search the web, run one-off CLI commands,
and anything else you can imagine.

If you're familiar with tools like [Alfred](https://www.alfredapp.com), [Wox](http://www.wox.one),
[ueli](https://ueli.oliverschwendener.ch), [Keypirinha](https://keypirinha.com), etc. you may already understand the concept. These
tools already work great. Command Center aims to fill a slightly different niche though, targeting hackers/programmers who enjoy and are familiar with working in the command line.

### Features/Goals

(***Note:*** *Command Center is still in active development. Some of these are still being worked on.*)

- Cross-platform (macOS, Windows, Linux)
- Can be run purely from the command line, in both standalone and daemon modes.
- Has its own terminal emulator that can be summoned with a hotkey (like a Quake console). Useful for running one-off commands when you're not already in a terminal window.
- Focus on not being resource-heavy or draining battery. While Electron can do many things, it's not resource-friendly, which is one reason why Command Center doesn't use it.
- Plugins are first-class. All the internal commands use the same plugin system a 3rd-party would use to integrate with Command Center. No scripting languages that only do a fraction of what the "real" thing can do.
- Automatic language detection. For example, if you type Japanese text, you can open the Japanese version of Google, Wikipedia, etc. as opposed to the default (English) versions.

## Example usage

![recording](assets/recording.gif?raw=true "Command Center screenshot")

#### Finding files

Start typing the name of the file you're looking for. You can also use the following commands:

- `find filename` - Find file by filename
- `in filename` - Find file by its contents

#### Music controls

- `play` - Start playing music (e.g. by default opens iTunes on macOS)
- `pause` - Pause the current track
- `stop` - Stop the current track
- `next` - Switch to the next track in your playlist
- `previous` - Switch to the previous track in your playlist
- `rate [1-5]` - Rate the current track

#### Timer

If you want to start a simple countdown timer:

`timer 15m -m "Call Sally"`

This will pop up a notification after 15 minutes. Specifying a message is optional.

#### Suspend/resume process

You can suspend (toggle) a process by its PID with:

`suspend 321`

The suspend command also has a configurable `suspendShortcut` attribute that is a global shortcut. This is
useful if you want to be able to pause applications that don't have a built-in pause feature, such as games. The idea
for this feature came from [Universal Pause Button](https://github.com/ryanries/UniversalPauseButton).

#### Calculator

You can use some built-in constructions and functions to quickly evaluate mathematical expressions:

- `numbers`, e. g. `5`, `-2.`, `+2.6`
- `constants` (case-insensitive):
    - `pi`
- `()` (parentheses for grouping)
- *symbolic operators*:
    - `+`, `-`, `*`, `/`, `%` (modulo), `^` (power), e. g. `5 * (3 - 4/2) ^ 2 % 3`
    - `^` has a higher precedence than any other symbolic operators, all of which has equal precedence
- *functions* (case-sensitive; have a higher precedence than symbolic operators):
    - with 1 parameter: `acos`, `asin`, `atan`, `cos`, `exp`, `log`, `sin`, `sqrt`, `tan`, `toDeg` (radians -> degrees),
    `toRad` (degrees -> radians), `!` (factorial)
    - with 2 parameters: `atan2`, `choose` (Newton's binomial), `gcd` (greatest common divisor)

Whitespaces between parts of an expression are ignored.

One-parametric functions can be written in two forms:
- separated by spaces for simple parameters (number/constant): `sin PI`, `sqrt 16`
- with parameters enclosed in parentheses for both simple and more complex expressions: `sin(PI / 2)`, `sqrt(9)`

The same applies to `choose`: `5 choose 3`, `(6-1) choose (2 + 1)`

Other two-parametric functions have to be written as follows: `atan2(1, 3)`, `gcd(20, 16)`

**Constraints:**
- for `n choose r`, both `n` and `r` have to be whole and non-negative, `n >= r`
- for `n!`, `n` has to be whole and non-negative
- for `gcd(a, b)`, both `a` and `b` have to be whole

## Installation

At the moment there is no simple "1-step install". You need to compile and generate an executable yourself (or run
directly from SBT).

`application.conf` is needed to run Command Center. The following locations are searched (in order):
1. `COMMAND_CENTER_CONFIG_PATH` (if defined)
2. `~/.commandcenter/application.conf`
3. `./application.conf`

**Important note**: For the pop-up emulator window to activate and come to the front properly on macOS you need to make sure you
place the `cc-tools` executable in the proper location. Either place it in `~/.command-center/cc-tools` or define the
`COMMAND_CENTER_TOOLS_PATH` environment variable if you want to specify a custom location.

## Development

Command Center mainly uses Scala with a strong focus on typed functional programming (via [ZIO](https://github.com/zio/zio)).
Types make developing plugins a much more pleasant experience as you get feedback quicker and features are more discoverable.
Writing a command is as simple as writing a single class [like so](https://github.com/reibitto/command-center/blob/master/core/src/main/scala/commandcenter/command/EpochUnixCommand.scala).

Once you start SBT, you should be presented a list of common commands. For example:

- `~compile` - Compile all modules with file-watch enabled
- `cli-client/run` - Run Command Center CLI client (interactive mode by default). Particularly useful for local development.
- `daemon/run` - Run Command Center in daemon mode (cmd+space to summon terminal emulator)
- `daemon/assembly` - Create an executable JAR for running in daemon mode
- `cli-client/assembly` - Create an executable JAR for running command line utility
- `cli-client/graalvm-native-image:packageBin` - Create a native executable of the CLI client

### Configuration

All commands can be configured. You can disable any core command, rename them, change their options, add new commands,
create aliases (useful for shortening argument passing), and so on.

#### Configuration format

Commands and options are configured with a single [HOCON](https://github.com/lightbend/config) configuration file. To see
an example, take a look at [application.conf](https://github.com/reibitto/command-center/blob/master/application.conf).

### Creating your own plugins

If you created a plugin and it's general-purpose and doesn't bring in extra dependencies, feel free to create an issue or PR
to get the command into Command Center's core commands. If not (or if you'd prefer to maintain the plugin yourself), you can
create your own separate repository to host it. If you let me know, I can add it to a list of external plugins.

Eventually the goal is to make installing external plugins as simple as running a single install command. See this issue [here](https://github.com/reibitto/command-center/issues/24).
Until then, it's a matter of dropping your jar file in the `plugins` folder and restarting the app.

## Contributing

There are a lot of issues marked as "good first issue" [here](https://github.com/reibitto/command-center/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22). Feel free to take any that interest you. I'd also appreciate
any help for OS-specific features. Help with Windows and Linux would be great since I've mainly been focusing on macOS for now.

Writing your own commands is a great way to begin learning ZIO. Each command can be developed, tested, and run in isolation.
For more help with ZIO, the [Discord channel](https://discordapp.com/invite/2ccFBr4) is a great place to ask questions.
