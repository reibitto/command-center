# Command Center

![Scala CI](https://github.com/reibitto/command-center/workflows/Scala%20CI/badge.svg)

*A CLI-based launcher and general productivity tool.*

## What is it?

Command Center is used to launch applications, find files, control music playback, search the web, run one-off CLI commands,
manage windows, and anything else you can imagine since it's customizable.

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

#### Window Manager

Global hotkeys can be mapped to various window management actions. For example:

- Minimize/maximize window 
- Center window
- Move window to next/previous monitor
- Cycle window location+size in each direction. For example, pressing win+alt+right the first time will
  move+resize the window to the right half of the screen. Pressing it again will resize it to 2/3 of the
  screen. And once more will be 1/3 of the screen. Then the cycle repeats.

*Note: This feature currently only supports Windows. The goal is to support all OSes soon.*

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
    - one-parametric: `!` (factorial), `acos`, `asin`, `atan`, `ceil`, `cos`, `cosh`, `exp`, `floor`, `ln`,
    `round`, `sin`, `sinh`, `sqrt`, `tan`, `tanh`, `toDeg` (radians -> degrees), `toRad` (degrees -> radians)
    - two-parametric: `atan2`, `choose` (Newton's binomial), `gcd` (greatest common divisor), `hypot` (square root of `x^2 + y^2`), `log`, `random`
    - multi-parametric: `max`, `min`

Whitespaces between parts of an expression are ignored.

One-parametric functions can be written in two forms:
- separated by spaces for simple parameters (number/constant): `sin PI`, `sqrt 16`
- with parameters enclosed in parentheses for both simple and more complex expressions: `sin(PI / 2)`, `sqrt(9)`.

Two- or multi-parametric functions can be written in three forms: `gcd 20 16`, or `gcd 20; 16`, or `gcd(20; 16)`
(`;` is here the configurable *parameterSeparator*).

`choose` has also an infix form: `6 choose 3`.

`max/min` can have more than two parameters: `max 0 5 (-6/4)`.

`random` has 3 flavours:
- `random` (zero-parametric): a random number from \[0, 1\]
- `random a b` (two-parametric): a random number from \[a, b\]
- `random int a b` (two-parametric): a whole random number from \[a, b\]

**Constraints:**
- for `n choose r`, both `n` and `r` have to be whole and non-negative, `n >= r`
- for `n!`, `n` has to be whole and non-negative
- for `gcd(a, b)`, both `a` and `b` have to be whole

**Parameters**

As different countries may have their own traditions for writing numbers (one of the obvious differences being the decimal separator &mdash; 
dot vs. comma), some of the most important parameters are determined locale-specifically or can be overriden in `application.conf` if needed:

```hocon
{
  type: "CalculatorCommand"
  decimalSeparator: ","
  groupingSeparator: "_"
  parameterSeparator: ";"
  groupingSize: 3
  groupingUsed: true
  maximumFractionDigits: 10
}
```
These settings apply both for parsing and displaying. `parameterSeparator` can be used with a multi-parametric function.
All other parameters correspond to properties of `DecimalFormat`/`DecimalFormatSymbols` of the same name.
Any of them can be omitted, their default values are then locale-specific. For the `parameterSeparator` the default is `;`. 

**List available operators/functions**

Type in `calculator functions` to see the full list of available operators and functions.

**List available configuration parameters**

Type in `calculator parameters` to see the full list of available configuration parameters for `application.conf`.

#### Calendar

You can add events to a configured calendar and view oncoming events. Currently only Google calendars are supported.

**Pre-configuring Google calendar**

To be able to view and modify events, you have to allow the calendar to be accessed from within the application.
Open the settings for your calendar and add `cccapp@commandcentercalendar.iam.gserviceaccount.com` to the *Share with specific people* section.
Select the option "Make changes to events".

![configuring Google calendar](assets/configure-google-calendar.png "Configuring Google calendar")

**Configuring application**

Then copy the calendar ID from the *Integrate calendar* section

![Google calendar ID](assets/configure-google-calendar_ID.png "Google calendar ID")

and paste it to the `application.conf`:

```hocon
type: "CalendarCommand"
client: {
  type: "Google"
  calendarId: "xyz@gmail.com"
}
```

There you can also configure the date and time format (used both for parsing and displaying) as well as provide some date aliases:

```hocon
formats: {
  dateFormat: "dd.MM.yyyy"
  timeFormat: "HH:mm"
  dayOffsets: {
    today: 0
    tomorrow: 1
    next week: 7
  }
}
```

**Listing oncoming events**

Use the `list` subcommand to get `n` oncoming events (by default `n == 3`):

`cal list 4`

**Adding new event**

For adding a new event there are two options:
- the simplest form: `<summary> @ <date/time>`:

`John's welcome party @ 19:00`
  
You can specify either date, or time, or both. Time without a date counts as a today event.

- if you want to provide more details use the full form:

`cal insert "An important event" --date tomorrow --time 18:00 --desc "A very important event, trust me" --loc Somewhere`

Available options are:
- `--date` (or `-d`) - (start) date
- `--time` (or `-t`) - optional (start) time; omit for all-day events
- `--enddate` - optional end date; if omitted, equals to start date
- `--endtime` - optional end time; required if start time is given
- `--desc` - optional detailed description
- `--loc` - optional location

Both for simple and full form you can use configured date aliases instead of concrete dates. For each specify a name and an offset (in days) from the current day.
You can also use localized names, e. g.
```
dayOffsets: {
  heute: 0
  morgen: 1
}
```

## Installation

At the moment there is no simple "1-step install". You need to compile and generate an executable yourself (or run
directly from SBT).

`application.conf` is needed to run Command Center. The following locations are searched (in order):
1. `COMMAND_CENTER_CONFIG_PATH` (if defined)
2. `~/.command-center/application.conf`
3. `./application.conf`

**Important note**: For the pop-up emulator window to activate and come to the front properly on macOS you need to make sure you
place the `cc-tools` executable in the proper location. Either place it in `~/.command-center/cc-tools` or define the
`COMMAND_CENTER_TOOLS_PATH` environment variable if you want to specify a custom location.

### Installing built-in optional plugins

Command Center comes with some optional plugins. These plugins are optional because they're either too niche or bring in
heavier dependencies that normal users wouldn't need. Currently there are:

#### `ject-plugin`
A real-time, offline dictionary plugin (currently only supports Japanese). This is based on [Ject](https://github.com/reibitto/ject).
You'll need to build the Lucene index first as described in the README. Then specify the command in `application.conf` like this:

```hocon
{
  type: "commandcenter.ject.JectCommand$",
  dictionaryPath: "/path/to/ject/data/lucene"
}
```

#### `stroke-order-plugin` 
Used to bring up the stroke order of kanji. This relies on having [Kanji stroke order font](https://www.nihilist.org.uk/)
installed. Also it requires using the terminal emulator as native CLI clients rarely allow changing the default font. Usage is `stroke 漢字`. The config
looks like this:

```hocon
{type: "commandcenter.strokeorder.StrokeOrderCommand$"}
```

#### Enabling the plugins

You can either set an sbt option or environment variable to enable the above plugins:

```bash
export COMMAND_CENTER_PLUGINS="stroke-order-plugin, ject-plugin"
```

or start up sbt like this:

```
sbt -Dcommand-center-plugins="ject-plugin, stroke-order-plugin"
```

To enable all plugins, you can also use `*`.

## Development

Command Center mainly uses Scala with a strong focus on typed functional programming (via [ZIO](https://github.com/zio/zio)).
Types make developing plugins a much more pleasant experience as you get feedback quicker and features are more discoverable.
Writing a command is as simple as writing a single class [like so](https://github.com/reibitto/command-center/blob/master/core/src/main/scala/commandcenter/command/EpochUnixCommand.scala).

Once you start SBT, you should be presented a list of common commands. For example:

- `~compile` - Compile all modules with file-watch enabled
- `cli/run` - Run Command Center CLI client (interactive mode by default). Particularly useful for local development.
- `emulator-swing/run` - Run the Command Center emulated terminal (cmd+space to summon terminal emulator)
- `emulator-swing/assembly` - Create an executable JAR for running in terminal emulator mode
- `cli/assembly` - Create an executable JAR for running command line utility
- `cli/graalvm-native-image:packageBin` - Create a native executable of the CLI client

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
Until then, it's a matter of dropping your jar file in the `plugins` folder and restarting the app. For more details on
external plugins, refer to the [plugins readme](emulator-swing/plugins/PLUGINS.md).

## Contributing

There are a lot of issues marked as "good first issue" [here](https://github.com/reibitto/command-center/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22). Feel free to take any that interest you. I'd also appreciate
any help for OS-specific features. Help with Windows and Linux would be great since I've mainly been focusing on macOS for now.

Writing your own commands is a great way to begin learning ZIO. Each command can be developed, tested, and run in isolation.
For more help with ZIO, the [Discord channel](https://discordapp.com/invite/2ccFBr4) is a great place to ask questions.
