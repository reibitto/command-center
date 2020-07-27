import Foundation
import AppKit

func activate() -> Void {
    let pid = Int32(CommandLine.arguments[2]).unsafelyUnwrapped

    NSRunningApplication.init(processIdentifier: pid).unsafelyUnwrapped.activate(options: NSApplication.ActivationOptions.activateIgnoringOtherApps)
}

func hide() -> Void {
    let pid = Int32(CommandLine.arguments[2]).unsafelyUnwrapped

    NSRunningApplication.init(processIdentifier: pid).unsafelyUnwrapped.hide()
}

func getClipboard() -> Void {
    if let s = NSPasteboard.general.pasteboardItems?.first?.string(forType: .string) {
        print(s)
    }
}

func setClipboard() -> Void {
    let text = CommandLine.arguments[2]

    let pasteboard = NSPasteboard.general
    pasteboard.declareTypes([NSPasteboard.PasteboardType.string], owner: nil)
    pasteboard.setString(text, forType: NSPasteboard.PasteboardType.string)
}

if(CommandLine.arguments.count <= 1) {
    print("Command Center Utility Tool")
    print()
    print("Usage: [subcommand] <args>")
    print("Subcommands:")
    print("  activate [pid]")
    print("  hide [pid]")
    print("  get-clipboard")
    print("  set-clipboard [text]")
} else {
    let command = CommandLine.arguments[1]

    if(command == "activate") {
        activate()
    } else if(command == "hide") {
        hide()
    } else if(command == "get-clipboard") {
        getClipboard()
    } else if(command == "set-clipboard") {
        setClipboard()
    }
}
