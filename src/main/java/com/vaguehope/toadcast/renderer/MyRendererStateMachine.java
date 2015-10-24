package com.vaguehope.toadcast.renderer;

import org.fourthline.cling.support.avtransport.impl.AVTransportStateMachine;
import org.teleal.common.statemachine.States;

@States({ MyRendererNoMediaPresent.class, MyRendererStopped.class, MyRendererPlaying.class })
public interface MyRendererStateMachine extends AVTransportStateMachine {}
