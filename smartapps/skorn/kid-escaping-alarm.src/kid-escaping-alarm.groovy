/**
 *  Copyright 2015 SmartThings
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
 *  Kid escape alarm
 *
 *  Author: Martin Lariz
 */
definition(
    name: "Kid escaping alarm",
    namespace: "skorn",
    author: "Martin Lariz",
    description: "Use combination of siren, door sensor and switch to detect child leaving through frontdoor unattended.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe@2x.png"
)

preferences {
    section("Door sensor to monitor.") {
        input "door", "capability.contactSensor", required: true
    }
    section("Siren to alarm with.") {
        input "siren", "capability.alarm", required: true
        input "sirenTrack", "number", title: "Which track to alarm with? (default: 5)", defaultValue: "5", required: true
        input "sirenRepeat", "number", title: "How often (in seconds) to repeat (should be duration of the alarm to keep constant)? (default: 10)", defaultValue: "10", required: true
    }
    section("Button(s) to disable alarm") {
        input "buttons", "capability.button", title: "Standard buttons", required: true, multiple: true
        input "buttonSleep", "number", title: "Sleep up to X seconds (before door opens, re-enables when closed)? (default: 60)", defaultValue: "60", required: true
    }
    section("Disable chirp?") {
        input "notifyButtons", "capability.button", title: "Play a sound when one of these buttons disables the alarm", required: false, multiple: true
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
    state.enabled = 1
    state.alarmLoops = 1
    state.alarming = 0
}

def doorHandler(evt) {
    if (evt.value == "open") {
        if (state.enabled == 1) {
            log.debug "Sending initial Alarm sequence."
            state.alarming = 1
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
    if (state.alarming == 1) {
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
        state.enabled = 0
        if (state.alarming == 1) {
            siren.stop()
            state.alarming = 0
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
    state.enabled = 1
}
