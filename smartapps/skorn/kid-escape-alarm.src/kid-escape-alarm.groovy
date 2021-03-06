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
        input "sirenTrack", "number", Title: "Which track to alarm with?", required: true
        input "sirenRepeat", "number", Title: "How often (in seconds) to repeat (should be duration of the alarm to keep constant)?", required: true
    }
    section("Switch used to disable alarm.") {
        input "master", "capability.switch", required: true
        input "switchSleep", "number", Title: "Sleep for how many seconds?", required: true
        input "switchInterval", "number", Title: "How many seconds to allow between switch presses?", required: true
    }
    section("Optional second button to disable alarm") {
        input "secondary", "capability.button", required: false
        input "buttonSleep", "number", Title: "Sleep for how many seconds?", required: true
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
    subscribe(master, "switch", switchHandler, [filterEvents: false])
    subscribe(secondary, "button", buttonHandler, [filterEvents: false])
    state.enabled = 1
    state.switchHistory = 0
}

def doorHandler(evt) {
    if (evt.value == "open") {
        if (state.enabled == 1) {
            keepAlarming()
        }
    } else if (evt.value == "closed") {
        siren.stop()
    }
}

def keepAlarming() {
    def currentValue = door.currentValue("contact")
    if (currentValue == "open" && state.enabled == 1) {
        siren.playTrack(sirenTrack)
        runIn(sirenRepeat, keepAlarming)
    }
}

def switchHandler(evt) {
    // TBD remove next 5 lines
    currentTime = now()
    log.debug "Current time: ${currentTime}"
    log.debug "Switch Name: ${evt.name}"
    log.debug "Switch Value: ${evt.value}"
    log.debug "Switch Physical: ${evt.physical}"

    if (evt.physical) {
        if (state.switchTS > ($currentTime - (switchInterval * 1000))) {
            // Expect up, up, down, down: reset if doesn't matchand if it does sleep alarm
            switch (state.switchHistory) {
                case 0:
                    if (evt.value == "on") {
                        log.debug "Sequence: Got first in sequence 'UP, up, down, down'"
                        state.switchHistory = 1
                    } else {
                        log.debug "Sequence: Isolated off, sequence reset"
                        state.switchHistory = 0
                    }
                    break
                case 1:
                    if (evt.value == "on") {
                        log.debug "Sequence: Got second in sequence 'UP, UP, down, down'"
                        state.switchHistory = 2
                    } else {
                        log.debug "Sequence: Second in sequence incorrect, sequence reset"
                        state.switchHistory = 0
                    }
                    break
                case 2:
                    if (evt.value == "off") {
                        log.debug "Sequence: Got third in sequence 'UP, UP, DOWN, down'"
                        state.switchHistory = 3
                    } else {
                        log.debug "Sequence: Third in sequence incorrect, sequence reset"
                        state.switchHistory = 0
                    }
                    break
                case 3:
                    if (evt.value == "off") {
                        log.debug "Sequence: Got full sequence 'UP, UP, DOWN, DOWN'"
                        sleepAlarm(switchSleep)
                    } else {
                        log.debug "Sequence: Final down in sequence incorrect, sequence reset"
                    }
                    state.switchHistory = 0
                    break
                default:
                    log.debug "Sequence: Got UNEXPECTED switchHistory, resetting sequence"
                    state.switchHistory = 0
                    break
            }
        } else {
            if (evt.value == "on" ) {
                log.debug "Sequence: Got first in sequence 'UP, up, down, down'"
                state.switchHistory = 1
            } else {
                log.debug "Sequence: Isolated off, sequence reset"
                state.switchHistory = 0
            }
        }
        state.switchTS = now()
    } else {
        log.trace "Skipping digital on/off event"
    }
}

def buttonHandler(evt) {
    // TBD remove next 5 lines
    currentTime = now()
    log.debug "Current time: ${currentTime}"
    log.debug "Button Name: ${evt.name}"
    log.debug "Button Value: ${evt.value}"
    log.debug "Button Physical: ${evt.physical}"

    if (evt.physical) {
        // TBD Ensure button press is captured properly, may need an "if" statement here
        sleepAlarm(buttonSleep)
    } else {
        log.trace "Skipping digital on/off event"
    }
}

def sleepAlarm(sleepTime) {
    state.enabled = 0
    siren.stop()
    runIn(sleepTime, enableAlarm)
}

def enableAlarm() {
    state.enabled = 1
}
