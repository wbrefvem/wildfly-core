/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.management.security;

import java.util.List;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handler to remove security realm definitions and remove the service.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SecurityRealmRemoveHandler implements OperationStepHandler {

    public static final SecurityRealmRemoveHandler INSTANCE = new SecurityRealmRemoveHandler();

    private SecurityRealmRemoveHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ModelNode model = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
        context.removeResource(PathAddress.EMPTY_ADDRESS);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final boolean reloadRequired = ManagementUtil.isSecurityRealmReloadRequired(context, operation);
                final String realmName = ManagementUtil.getSecurityRealmName(operation);
                if (reloadRequired) {
                    context.reloadRequired();
                } else {
                    removeServices(context, realmName, model);
                }

                context.completeStep(new OperationContext.RollbackHandler() {
                    @Override
                    public void handleRollback(OperationContext context, ModelNode operation) {
                        if (reloadRequired) {
                            context.revertReloadRequired();
                        } else {
                            recoverServices(context, realmName, model);
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    protected void removeServices(final OperationContext context, final String realmName, final ModelNode model) throws OperationFailedException {
        // KISS -- Just remove the service and all child services.
        ServiceName realmServiceName = SecurityRealm.ServiceUtil.createServiceName(realmName);
        ServiceRegistry serviceRegistry = context.getServiceRegistry(false);
        List<ServiceName> allNames = serviceRegistry.getServiceNames();
        for (ServiceName current : allNames) {
            if (realmServiceName.isParentOf(current)) {
                context.removeService(current);
            }
        }
    }

    protected void recoverServices(OperationContext context, final String realmName, ModelNode model) {
        try {
            SecurityRealmAddHandler.INSTANCE.installServices(context, realmName, model);
        } catch (OperationFailedException e) {
            throw ControllerLogger.ROOT_LOGGER.failedToRecoverServices(e);
        }
    }
}
