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
    name: "Kid escape alarm",
    namespace: "skorn",
    author: "Martin Lariz",
    description: "Use combination of siren, door sensor and button (currently switch as I don't have a button) to detect child leaving throug frontdoor unattended.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe@2x.png"
)

preferences {
	section("Door sensor to monitor.") {
		input "door", "capability.sensor", required: true
	}
	section("Switch (or button in future) to disable alarm.") {
		input "master", "capability.switch", required: true
	}
	section("Siren to alarm with.") {
		input "siren", "capability.alarm", required: true
	}
	section("Optional second button to disable alarm") {
		input "secondary", "capability.button", required: false
	}
}

def installed()
{
	itialize()
}

def updated()
{
	unsubscribe()
	itialize()
}

def initialize()
{
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
    }
}

def keepAlarming() {
    def currentValue = door.currentValue("contact")
    if (currentValue == "open" && state.enabled == 1) {
        siren.musicPlayer.playTrack(6)
        runIn(1, keepAlarming)
    }
}

def switchHandler(evt) {
    log.debug now()
	log.info evt.value

	if (evt.physical) {
        // Allow only up to 3 seconds in between presses, else reset
		if (state.switchTS > (now() - 3000)) {
            // Expect up, up, down, down: reset if doesn't matchand if it does sleep alarm 60 seconds
            switch (state.switchHistory) {
                case 0:
                    if (evt.value == "on") {
                        state.switchHistory = 1
                    } else {
                        state.switchHistory = 0
                    }
                    break
                case 1:
                    if (evt.value == "on") {
                        state.switchHistory = 2
                    } else {
                        state.switchHistory = 0
                    }
                    break
                case 2:
                    if (evt.value == "off") {
                        state.switchHistory = 3
                    } else {
                        state.switchHistory = 0
                    }
                    break
                case 3:
                    if (evt.value == "off") {
                        sleepAlarm(60)
                    }
                    state.switchHistory = 0
                    break
                default:
                    state.switchHistory = 0
                    break
            }
		} else {
            if (evt.value == "on" ) {
                state.switchHistory = 1
            } else {
                state.switchHistory = 0
            }
        }
        state.switchTS = now()
	} else {
		log.trace "Skipping digital on/off event"
	}
}

def sleepAlarm(sleepTime) {
    state.enabled = 0
    runIn(sleepTime, enableAlarm)
}

def enableAlarm() {
    state.enabled = 1
}