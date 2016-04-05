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
        input "sirenTrack", "number", title: "Which track to alarm with? (default: 1)", defaultValue: "1", required: true
        input "sirenRepeat", "number", title: "How often (in seconds) to repeat (should be duration of the alarm to keep constant)? (default: 10)", defaultValue: "10", required: true
    }
    section("Switch(es) to disable alarm. (sequence to disable is up, up, down, down") {
        input "switches", "capability.switch", required: false, multiple: true
        input "switchSleep", "number", title: "Sleep for how many seconds? (default: 60)", defaultValue: "60", required: true
        input "switchInterval", "number", title: "How many seconds to allow between switch presses? (default: 4)", defaultValue: "4", required: true
    }
    section("Button(s) to disable alarm") {
        input "buttons", "capability.button", required: false, multiple: true
        input "buttonSleep", "number", title: "Sleep for how many seconds? (default: 60)", defaultValue: "60", required: true
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
    subscribe(switches, "switch", switchHandler, [filterEvents: false])
    subscribe(buttons, "button", buttonHandler, [filterEvents: false])
    state.clear()
    state.enabled = 1
    state.alarmLoops = 1
    switches.each {
        state[it.displayName] = [history: 0, ts: 0]
    }
}

def doorHandler(evt) {
    if (evt.value == "open") {
        if (state.enabled == 1) {
            log.debug "Sending initial Alarm sequence."
            keepAlarming()
        } else {
            log.debug "Got open on door but alarming disabled."
        }
    } else if (evt.value == "closed") {
        log.debug "Stopping any current alarms."
        state.alarmLoops = 1
        siren.stop()
    }
}

def keepAlarming() {
    def currentValue = door.currentValue("contact")
    if (currentValue == "open" && state.enabled == 1) {
        log.debug "Door is open and alarming enabled, playing a loop (repeated ${state.alarmLoops} times)"
        state.alarmLoops = state.alarmLoops + 1
        siren.playTrack(sirenTrack)
        runIn(sirenRepeat, keepAlarming)
    }
}

def switchHandler(evt) {
    if (evt.physical) {
        def myMap = state[evt.displayName]
        def currentTime = now()
        if (myMap['ts'] > (currentTime - (switchInterval * 1000))) {
            // Expect up, up, down, down: reset if doesn't matchand if it does sleep alarm
            switch (myMap['history']) {
                case 0:
                    if (evt.value == "on") {
                        log.debug "Sequence: Got first in sequence 'UP, up, down, down'"
                        myMap['history'] = 1
                    } else {
                        log.debug "Sequence: Isolated off, sequence reset"
                        myMap['history'] = 0
                    }
                    break
                case 1:
                    if (evt.value == "on") {
                        log.debug "Sequence: Got second in sequence 'UP, UP, down, down'"
                        myMap['history'] = 2
                    } else {
                        log.debug "Sequence: Second in sequence incorrect, sequence reset"
                        myMap['history'] = 0
                    }
                    break
                case 2:
                    if (evt.value == "off") {
                        log.debug "Sequence: Got third in sequence 'UP, UP, DOWN, down'"
                        myMap['history'] = 3
                    } else {
                        log.debug "Sequence: Third in sequence incorrect, sequence reset"
                        myMap['history'] = 0
                    }
                    break
                case 3:
                    if (evt.value == "off") {
                        log.debug "Sequence: Got full sequence 'UP, UP, DOWN, DOWN'"
                        sleepAlarm(switchSleep)
                    } else {
                        log.debug "Sequence: Final down in sequence incorrect, sequence reset"
                    }
                    myMap['history'] = 0
                    break
                default:
                    log.debug "Sequence: Got UNEXPECTED switchHistory, resetting sequence"
                    myMap['history'] = 0
                    break
            }
        } else {
            if (evt.value == "on" ) {
                log.debug "Sequence: Got first in sequence 'UP, up, down, down'"
                myMap['history'] = 1
            } else {
                log.debug "Sequence: Isolated off, sequence reset"
                myMap['history'] = 0
            }
        }
        myMap['ts'] = now()
    } else {
        log.trace "Skipping digital on/off event"
    }
}

def buttonHandler(evt) {
    log.debug "Got button press, sleeping"
    sleepAlarm(buttonSleep)
}

def sleepAlarm(sleepTime) {
    log.debug "Sleeping escaping alarm for ${sleepTime} seconds"
    state.enabled = 0
    siren.stop()
    runIn(sleepTime, enableAlarm)
}

def enableAlarm() {
    state.enabled = 1
}
