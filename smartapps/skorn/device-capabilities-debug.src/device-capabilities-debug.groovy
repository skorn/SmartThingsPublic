/**
 *  device capabilities debug
 *
 *  Copyright 2016 Martin Lariz
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
    name: "device capabilities debug",
    namespace: "skorn",
    author: "Martin Lariz",
    description: "log capabilities of a device",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    section("Title") {
        input "mySwitch", "capability.contactSensor"
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
    def mySwitchCaps = mySwitch.capabilities
    // log each capability supported by the "mySwitch" device, along
    // with all its supported commands
    mySwitchCaps.each {cap ->
        log.debug "${mySwitch.name},${cap.name},--"
        cap.commands.each {comm ->
            log.debug "${mySwitch.name},${cap.name},${comm.name}"
        }
    }
    subscribe(mySwitch, "contactSensor", logIt)
    subscribe(mySwitch, "sensor", logSensor)
    subscribe(mySwitch, "contact", logContact)
    subscribe(mySwitch, "battery", logBattery)
}

def logIt(evt) {
    log.debug "Got event ${evt}"
    log.debug "Value: ${evt.value}"
}
def logSensor(evt) {
    log.debug "sensor Got event ${evt}"
    log.debug "sensor Value: ${evt.value}"
}
def logContact(evt) {
    log.debug "contact Got event ${evt}"
    log.debug "contact Value: ${evt.value}"
}
def logBattery(evt) {
    log.debug "battery Got event ${evt}"
    log.debug "battery Value: ${evt.value}"
}
