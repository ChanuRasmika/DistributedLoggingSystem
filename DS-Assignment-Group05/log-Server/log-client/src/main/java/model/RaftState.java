package model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RaftState {
    private Role currentState;
}