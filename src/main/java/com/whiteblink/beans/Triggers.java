package com.whiteblink.beans;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Triggers {
    private int id;
    private String journey_ID;
    private String trigger_data;
}
