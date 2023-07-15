Add-Type -AssemblyName System.Speech;
$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer;
$speak.SelectVoice('{0}');
$speak.Speak('{1}');
