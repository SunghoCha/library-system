//package msa.bookloan.adapter.in.messaging.inbox.handler.saga.member;
//
//import lombok.RequiredArgsConstructor;
//import msa.bookloan.adapter.in.messaging.inbox.InboxMessage;
//import msa.bookloan.adapter.in.messaging.inbox.handler.InboxEventHandler;
//import msa.bookloan.application.saga.LoanRequestSagaOrchestrator;
//import msa.bookloan.application.saga.reply.member.MemberCheckedInternalEvent;
//import msa.common.events.bookloan.saga.reply.SagaReplyType;
//import msa.common.events.bookloan.saga.reply.member.MemberCheckedReply;
//import org.springframework.stereotype.Component;
//
//@Deprecated
//@Component
//@RequiredArgsConstructor
//public class MemberCheckedInboxHandler implements InboxEventHandler<MemberCheckedReply> {
//
//    private final LoanRequestSagaOrchestrator orchestrator;
//
//    @Override
//    public String eventType() {
//        return SagaReplyType.MEMBER_CHECKED.getValue();
//    }
//
//    @Override
//    public Class<MemberCheckedReply> payloadType() {
//        return MemberCheckedReply.class;
//    }
//
//    @Override
//    public void handle(InboxMessage<MemberCheckedReply> message) {
//        MemberCheckedReply payload = message.payload();
//        MemberCheckedInternalEvent internal = MemberCheckedInternalEvent.from(payload);
//        orchestrator.onMemberChecked(internal);
//    }
//}
