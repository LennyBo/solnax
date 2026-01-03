package com.rose.solnax.model.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InstantPower {
    Double solar;
    Double house;
    Double heat;
    Double evCharger;
}
