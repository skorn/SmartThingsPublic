/**
 *  Copyright 2016 Martin Lariz
 *
 *  Author: Martin Lariz
 *
 *  Kid escaping alarm
 *    This app is designed to monitor a Front Door (though multiple are supported), to ensure that there are no
 *  unattended children attempt to elope. The track you cannot replay more than every 10 seconds, and should be longer
 *  than that (recommend at least 30 seconds at minimum) to reduce frequency of "runIn" loops, and therefore Smartthings 
 *  server load. Pressing one of the approved buttons will allow a window of time to open the door without setting off the
 *  alarm. It will remain off until the door is then shut again. Should the alarm be triggered it will continue to alarm
 *  until one of the buttons is pressed (or a specified number of loops is played).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Kid escaping alarm",
    namespace: "skorn",
    author: "Martin Lariz",
    description: "Use combination of siren, door sensor and button to detect child leaving through frontdoor unattended.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe@2x.png"
)

preferences {
    section("Door sensor to monitor.") {
        input "door", "capability.contactSensor", required: true
    }
    section("Siren to alarm with.") {
        input "siren", "capability.musicPlayer", required: true
        input "sirenTrack", "number", title: "Which track to alarm with? (default: 5)", defaultValue: "5", required: true
        input "sirenRepeat", "number", title: "How often (in seconds) to repeat (set to track duration to keep constant)? (default: 30, range: 10-900)", defaultValue: "10", range: "10..900", required: true
        input "sirenMaxLoops", "number", title: "Maximum number of loops to play? (default: 99, range: 1-99)", defaultValue: "99", range: "1..99", required: true
    }
    section("Button(s) to disable alarm") {
        input "buttons", "capability.button", title: "Standard buttons", required: true, multiple: true
        input "buttonSleep", "number", title: "Allow X seconds to open door (closing re-arms)? (default: 60, range: 5-300)", defaultValue: "60", range: "5..300", required: true
    }
    section("Disable chirp?") {
        input "notifyButtons", "capability.button", title: "Play a track when one of these buttons disables the alarm", required: false, multiple: true
        input "notifyChirp", "number", title: "Track to play when alert is temporarily disabled (0 disables)", defaultValue: "0", required: true
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(door, "contact", doorHandler)
    subscribe(buttons, "button", buttonHandler)
    state.clear()
    state.enabled = true
    state.alarmLoops = 1
    state.alarming = false
}

def doorHandler(evt) {
    if (evt.value == "open") {
        if (state.enabled) {
            log.debug "Sending initial Alarm sequence."
            state.alarming = true
            keepAlarming()
        } else {
            log.debug "Got open on door but alarming disabled."
        }
    } else if (evt.value == "closed") {
        enableAlarm()
    }
}

def keepAlarming() {
    def currentValue = door.currentValue("contact")
    if (state.alarming) {
        log.debug "Door opened without button press triggering alarm, playing a loop (repeated ${state.alarmLoops} times)"
        state.alarmLoops = state.alarmLoops + 1
        siren.playTrack(sirenTrack)
        runIn(sirenRepeat, keepAlarming)
        log.debug "Sleeping for sirenRepeat seconds"
    }
}

def buttonHandler(evt) {
    def buttonNumber = parseJson(evt.data)?.buttonNumber
    if (buttonNumber == 1) {
        log.debug "Sleeping escaping alarm for ${buttonSleep} seconds"
        state.enabled = false
        if (state.alarming) {
            siren.stop()
            state.alarming = false
        }
        notifyButtons.each { notifyButton ->
            if (notifyButton.displayName == evt.displayName && notifyChirp != 0) {
                log.debug "Playing ${notifyChirp} track to notify people this is disabled"
                siren.playTrack(notifyChirp)
            }
        }
    }
    runIn(buttonSleep, enableAlarm)
}

def enableAlarm() {
    state.enabled = true
}
