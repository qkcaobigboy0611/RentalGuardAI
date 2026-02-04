/**
 * @author qkcao
 * @date 2026/1/30 15:59
 */
package com.rental.guard.ai.config;


import com.rental.guard.ai.domain.enum1.FraudPreventionEvent;
import com.rental.guard.ai.domain.enum1.FraudPreventionState;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.*;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;

@Configuration
@EnableStateMachine
public class FraudPreventionStateMachineConfig
        extends EnumStateMachineConfigurerAdapter<FraudPreventionState, FraudPreventionEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<FraudPreventionState, FraudPreventionEvent> states)
            throws Exception {
        states
                .withStates()
                .initial(FraudPreventionState.INITIAL)
                .states(EnumSet.allOf(FraudPreventionState.class))
                .end(FraudPreventionState.COMPLETED)
                .end(FraudPreventionState.ESCALATED_TO_HUMAN);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<FraudPreventionState, FraudPreventionEvent> transitions)
            throws Exception {
        transitions
                // 初始状态转换
                .withExternal()
                .source(FraudPreventionState.INITIAL)
                .target(FraudPreventionState.VERIFY_PROPERTY)
                .event(FraudPreventionEvent.USER_PROVIDED_DETAILS)
                .action(verifyPropertyAction())
                .and()
                // 房源验证转换
                .withExternal()
                .source(FraudPreventionState.VERIFY_PROPERTY)
                .target(FraudPreventionState.ASSESS_PAYMENT_RISK)
                .event(FraudPreventionEvent.PROPERTY_VERIFIED)
                .guard(propertyVerifiedGuard())
                .and()
                .withExternal()
                .source(FraudPreventionState.VERIFY_PROPERTY)
                .target(FraudPreventionState.ESCALATED_TO_HUMAN)
                .event(FraudPreventionEvent.HIGH_RISK_DETECTED)
                .action(escalateAction())
                .and()
                // 风险评估转换
                .withExternal()
                .source(FraudPreventionState.ASSESS_PAYMENT_RISK)
                .target(FraudPreventionState.ASSESS_OVERALL_RISK)
                .event(FraudPreventionEvent.NEXT_STEP)
                .and()
                // 高风险处理
                .withExternal()
                .source(FraudPreventionState.ASSESS_OVERALL_RISK)
                .target(FraudPreventionState.ESCALATED_TO_HUMAN)
                .event(FraudPreventionEvent.HIGH_RISK_DETECTED)
//                .action(highRiskAction()) todo
                .and()
                // 低风险完成
                .withExternal()
                .source(FraudPreventionState.ASSESS_OVERALL_RISK)
                .target(FraudPreventionState.PROVIDE_SAFETY_ADVICE)
                .event(FraudPreventionEvent.LOW_RISK_DETECTED);
//                .action(lowRiskAction()); todo
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<FraudPreventionState, FraudPreventionEvent> config)
            throws Exception {
        config
                .withConfiguration()
                .autoStartup(true)
                .listener(listener());
    }

    private StateMachineListener<FraudPreventionState, FraudPreventionEvent> listener() {
        return new StateMachineListenerAdapter<FraudPreventionState, FraudPreventionEvent>() {
            @Override
            public void stateChanged(State<FraudPreventionState, FraudPreventionEvent> from,
                                     State<FraudPreventionState, FraudPreventionEvent> to) {
                System.out.println("State changed from " + from.getId() + " to " + to.getId());
            }
        };
    }

    private org.springframework.statemachine.action.Action<FraudPreventionState, FraudPreventionEvent> verifyPropertyAction() {
        return context -> {
            System.out.println("Executing property verification action");
            // 这里调用房源验证工具
        };
    }

    private org.springframework.statemachine.guard.Guard<FraudPreventionState, FraudPreventionEvent> propertyVerifiedGuard() {
        return context -> {
            Object verificationResult = context.getExtendedState().getVariables().get("propertyVerificationResult");
            return verificationResult != null && Boolean.TRUE.equals(verificationResult);
        };
    }

    private org.springframework.statemachine.action.Action<FraudPreventionState, FraudPreventionEvent> escalateAction() {
        return context -> {
            System.out.println("Escalating to human agent");
            // 这里实现转人工逻辑
        };
    }
}
