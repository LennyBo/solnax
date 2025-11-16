package com.rose.solnax.process.adapters.meters;


//Everything in watts
public interface IPowerMeter {

    //Positive means power is leaving house (exporting), negative means power is coming into the house (importing)
    Double gridMeter();

    Double solarMeter();
}
