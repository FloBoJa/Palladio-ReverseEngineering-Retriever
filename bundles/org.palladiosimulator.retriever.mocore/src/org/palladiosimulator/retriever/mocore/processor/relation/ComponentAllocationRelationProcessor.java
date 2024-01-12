package org.palladiosimulator.retriever.mocore.processor.relation;

import org.palladiosimulator.retriever.mocore.surrogate.PcmSurrogate;
import org.palladiosimulator.retriever.mocore.surrogate.relation.ComponentAllocationRelation;

import tools.mdsd.mocore.framework.processor.RelationProcessor;

public class ComponentAllocationRelationProcessor extends RelationProcessor<PcmSurrogate, ComponentAllocationRelation> {
    public ComponentAllocationRelationProcessor(final PcmSurrogate model) {
        super(model, ComponentAllocationRelation.class);
    }
}
