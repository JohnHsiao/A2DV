package mcp;

import java.io.IOException;

/*
 * #%L
 * **********************************************************************
 * ORGANIZATION  :  Pi4J
 * PROJECT       :  Pi4J :: Java Examples
 * FILENAME      :  MCP3008GpioExampleNonMonitored.java  
 * 
 * This file is part of the Pi4J project. More information about 
 * this project can be found here:  http://www.pi4j.com/
 * **********************************************************************
 * %%
 * Copyright (C) 2012 - 2015 Pi4J
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
import com.pi4j.gpio.extension.base.AdcGpioProvider;
import com.pi4j.gpio.extension.mcp.MCP3008GpioProvider;
import com.pi4j.gpio.extension.mcp.MCP3008Pin;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.gpio.event.GpioPinAnalogValueChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
import com.pi4j.io.spi.SpiChannel;
import com.pi4j.io.spi.SpiDevice;

/**
 * <p>
 * This example code demonstrates how to setup a custom GpioProvider
 * for analog output pin using the MCP3008 ADC chip.  This example
 * configures the MCP3008 without background monitoring and eventing.
 * </p>
 *
 * <p>
 * This GPIO provider implements the MCP3008 10-Bit Analog-to-Digital Converter (ADC) as native Pi4J GPIO pins.
 * </p>
 *
 * <p>
 * The MCP3008 is connected via SPI connection to the Raspberry Pi and provides 8 GPIO analog input pins.
 * </p>
 *
 * @author Christian Wehrli, Robert Savage
 */
public class MCP3008 {
	
    GpioController gpio = GpioFactory.getInstance();
    AdcGpioProvider provider;
    GpioPinAnalogInput input;
    int value;
    
    public void init() throws IOException, InterruptedException{
    	provider=new MCP3008GpioProvider(SpiChannel.CS0);
    	input=gpio.provisionAnalogInputPin(provider, MCP3008Pin.CH0, "CH0");
    	provider.setEventThreshold(100, input); 
        provider.setMonitorInterval(250); // milliseconds            
        
        GpioPinListenerAnalog listener = new GpioPinListenerAnalog(){
            @Override
            public void handleGpioPinAnalogValueChangeEvent(GpioPinAnalogValueChangeEvent event){                    
                value=(int) event.getValue();
            }
        };
        
        gpio.addListener(listener, input);
        for(int i=0; i<600; i++){
        	Thread.sleep(1000);
        }            
    }    
    
    public int getValue(){        	
    	return value;
    }
    
    public void stop(){
    	gpio.shutdown();
    }        
}
