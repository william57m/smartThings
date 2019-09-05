definition(
    name: "Sinopé Technologies Inc. service manager",
    namespace: "Sinopé Technologies Inc.",
    author: "jérémie",
    description: "description",
    category: "Mode Magic",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/good-night.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/good-night@2x.png"
    
)

preferences {
  	section ("Devices to connect") {
    	input "switches", "capability.switch", title: "Which switch, dimmer or load controller?", multiple: true, required: false
    	input "thermostats", "capability.thermostatHeatingSetpoint", title: "Which thermostat?", multiple: true, required: false
    }
    section ("Account info") {
        input "email", "text", title: "Your neviweb® account login e-mail", description: "Your neviweb® account login e-mail"
        input "password", "password", title: "Your neviweb® account login password", description: "Your neviweb® account login password"
    }
}


def refreshDevices(data){
    def output = ["session": getSessionId(), "action": "refresh"]
    def deviceToBeRefreshed
    switches.each{device ->
        if( device.id == data.deviceId ){
            deviceToBeRefreshed = device
        }
    }
    thermostats.each{device ->
        if( device.id == data.deviceId ){
            deviceToBeRefreshed = device
        }
    }
    if(output?.session){
        deviceToBeRefreshed.StartCommunicationWithServer(output)
    }
}


def uninstalled() {
    unsubscribe()
    unschedule()
    logout();
}

def updated() {
    // log.info "Updating Service Manager"
	state.server="https://smartthings.neviweb.com/"
	unsubscribe()
    unschedule()

    switches.each{device ->
        runEvery1Minute(refreshDevices,[data:[deviceId:device?.id]])
    }
    thermostats.each{device ->
        runEvery5Minutes(refreshDevices,[data:[deviceId:device?.id]])
    }
    state.sessionTime = 0;

    subscribe(switches, "switch", startDevicesCommunicationsHandler)
    subscribe(switches, "level", startDevicesCommunicationsHandler)
    subscribe(thermostats, "thermostat", startDevicesCommunicationsHandler)
}
def installed() {
    subscribe(switches, "switch", startDevicesCommunicationsHandler)
    subscribe(switches, "level", startDevicesCommunicationsHandler)
    subscribe(thermostats, "thermostat", startDevicesCommunicationsHandler)
}


def startDevicesCommunicationsHandler(evt){
    def data = parseJson(evt.data)
    data?.session = getSessionId()
    if(data?.action == "resetSession"){
        logout()
        login()
    }else if(data?.session && data?.deviceId){
        // log.debug "RECEIVED : ${data}"
        getDeviceObj(data.deviceId)?.StartCommunicationWithServer(data);
    }
}

private getDeviceObj(deviceId){
    def deviceToReturn = null;
    switches.each{device ->
    	if(device?.id == deviceId) {
            deviceToReturn = device;
        }
    }
    thermostats.each{device ->
    	if(device?.id == (deviceId)) {
            deviceToReturn = device;
        }
    }
    if(deviceToReturn){
        return deviceToReturn
    }else{
        // log.error "no device with id  "+ deviceId
        return null
    }
}

def getSessionId(){
	if(state?.session && state?.sessionTime && 
    state.sessionTime < now() && 
    now() < state.sessionTime+590000 ){
		state.sessionTime = now();
		return state.session
	}
    else{
        if(state.session){
            logout();
        }
        return login();
    }
}

def login() {
    def params = [
        uri: "${state.server}",
        path: 'login',
        requestContentType: "application/json; charset=UTF-8",
        body: ["username": settings.email, "password": settings.password, "interface": "neviweb"]
    ]
	if(data?.session){
    	logout();
	}
    return requestApi("login", params);
}

def logout() {
    def params = [
		uri: "${state.server}",
        path: "logout",
       	requestContentType: "application/json, text/javascript, */*; q=0.01",
        headers: ['Session-Id' : session]
   	]
    requestApi("logout", params)
}

def requestApi(actionApi, params){
    params.uri = "https://smartthings.neviweb.com/"
	// // log.info("requestApi - ${actionApi}, -> ${params}");
	switch(actionApi){
		case "login":
			httpPost(params) { resp ->
			// // log.info(resp.data)
			if (resp?.data?.error){
				log.error(resp.data.error)
                return null;
			}else{
				// log.info("New access token")
				state.session=resp.data.session
				state.sessionTime = now();
				// // log.info("Session : " + state.session)
                return state.session
			}
		}
		break;
		case "logout":
			params.path = "logout";
			params.headers = ['Session-Id' : state?.session]
			httpGet(params) {resp ->

				if(resp?.data?.success == false){
					// log.warn("logout failure ${resp.data}");
                    
				}else{
					// // log.info("logout successful ${resp.data}");
				}
			}
		break;
	}

}