/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 * <p>
 * This file is part of Qcadoo.
 * <p>
 * Qcadoo is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 * ***************************************************************************
 */
package com.qcadoo.mes.materialFlowResources.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.materialFlowResources.constants.DocumentFields;
import com.qcadoo.mes.materialFlowResources.constants.DocumentState;
import com.qcadoo.mes.materialFlowResources.constants.DocumentType;
import com.qcadoo.mes.materialFlowResources.constants.MaterialFlowResourcesConstants;
import com.qcadoo.mes.materialFlowResources.constants.PositionFields;
import com.qcadoo.mes.materialFlowResources.exceptions.DocumentBuildException;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.security.api.UserService;

public class DocumentBuilder {

    private final DataDefinitionService dataDefinitionService;

    private final ResourceManagementService resourceManagementService;

    private final ReceiptDocumentForReleaseHelper receiptDocumentForReleaseHelper;

    private final Entity document;

    private final List<Entity> positions = Lists.newArrayList();

    DocumentBuilder(final DataDefinitionService dataDefinitionService, final ResourceManagementService resourceManagementService,
                    final UserService userService, final ReceiptDocumentForReleaseHelper receiptDocumentForReleaseHelper) {
        this.dataDefinitionService = dataDefinitionService;
        this.resourceManagementService = resourceManagementService;
        this.receiptDocumentForReleaseHelper = receiptDocumentForReleaseHelper;
        this.document = createDocument(userService);
    }

    DocumentBuilder(final DataDefinitionService dataDefinitionService, final ResourceManagementService resourceManagementService,
                    final ReceiptDocumentForReleaseHelper receiptDocumentForReleaseHelper, final Entity user) {
        this.dataDefinitionService = dataDefinitionService;
        this.resourceManagementService = resourceManagementService;
        this.receiptDocumentForReleaseHelper = receiptDocumentForReleaseHelper;
        this.document = createDocument(user);
    }

    public Entity getDocument() {
        return document;
    }

    public DocumentBuilder receipt(final Entity locationTo) {
        document.setField(DocumentFields.LOCATION_TO, locationTo);
        document.setField(DocumentFields.TYPE, DocumentType.RECEIPT.getStringValue());

        return this;
    }

    public DocumentBuilder internalOutbound(Entity locationFrom) {
        document.setField(DocumentFields.LOCATION_FROM, locationFrom);
        document.setField(DocumentFields.TYPE, DocumentType.INTERNAL_OUTBOUND.getStringValue());

        return this;
    }

    public DocumentBuilder internalInbound(Entity locationTo) {
        document.setField(DocumentFields.LOCATION_TO, locationTo);
        document.setField(DocumentFields.TYPE, DocumentType.INTERNAL_INBOUND.getStringValue());

        return this;
    }

    public DocumentBuilder transfer(Entity locationTo, Entity locationFrom) {
        document.setField(DocumentFields.LOCATION_TO, locationTo);
        document.setField(DocumentFields.LOCATION_FROM, locationFrom);
        document.setField(DocumentFields.TYPE, DocumentType.TRANSFER.getStringValue());

        return this;
    }

    public DocumentBuilder release(Entity locationFrom) {
        document.setField(DocumentFields.LOCATION_FROM, locationFrom);
        document.setField(DocumentFields.TYPE, DocumentType.RELEASE.getStringValue());

        return this;
    }

    public DocumentBuilder returned(final Entity locationTo) {
        document.setField(DocumentFields.LOCATION_TO, locationTo);
        document.setField(DocumentFields.TYPE, DocumentType.RETURN.getStringValue());

        return this;
    }

    /**
     * Add position to document, use this method for outbound and transfer documents where additional attributes should not been
     * set.
     *
     * @param product
     * @param quantity
     * @return DocumentBuilder.this
     */
    public DocumentBuilder addPosition(final Entity product, final BigDecimal quantity) {
        return addPosition(product, quantity, null, null, null, null);
    }

    /**
     * Add position to document, use this method for outbound and transfer documents for locations with manual algorithm
     *
     * @param product
     * @param quantity
     * @param resource
     * @return DocumentBuilder.this
     */
    public DocumentBuilder addPosition(final Entity product, final BigDecimal quantity, final Entity resource) {
        return addPosition(product, quantity, null, null, null, null, resource);
    }

    /**
     * Add position to document, use this method for inbound documents where additional attributes are required sometimes.
     *
     * @param product
     * @param quantity
     * @param price
     * @param batch
     * @param expirationDate
     * @param productionDate
     * @return DocumentBuilder.this
     */
    public DocumentBuilder addPosition(final Entity product, final BigDecimal quantity, final BigDecimal price,
                                       final String batch, final Date productionDate, final Date expirationDate) {
        return addPosition(product, quantity, price, batch, productionDate, expirationDate, null);
    }

    public DocumentBuilder addPosition(final Entity product, final BigDecimal quantity, final BigDecimal price,
                                       final String batch, final Date productionDate, final Date expirationDate, final Entity resource) {
        Preconditions.checkArgument(product != null, "Product argument is required.");
        Preconditions.checkArgument(quantity != null, "Quantity argument is required.");

        Entity position = createPosition(product, quantity, price, batch, productionDate, expirationDate, resource);

        positions.add(position);

        return this;
    }

    public DocumentBuilder addPosition(final Entity product, final BigDecimal quantity, final BigDecimal givenQuantity,
                                       final String givenUnit, final BigDecimal conversion, final BigDecimal price, final String batch,
                                       final Date productionDate, final Date expirationDate, final Entity resource) {
        Preconditions.checkArgument(product != null, "Product argument is required.");
        Preconditions.checkArgument(quantity != null, "Quantity argument is required.");

        Entity position = createPosition(product, quantity, givenQuantity, givenUnit, conversion, price, batch, productionDate,
                expirationDate, resource);

        positions.add(position);

        return this;
    }

    public DocumentBuilder addPosition(final Entity product, final BigDecimal quantity, final BigDecimal givenQuantity,
                                       final String givenUnit, final BigDecimal conversion, final BigDecimal price, final String batch,
                                       final Date productionDate, final Date expirationDate, final Entity resource, final Entity storageLocation,
                                       final Entity palletNumber, final String typeOfPallet, final Entity additionalCode, final boolean isWaste) {
        Preconditions.checkArgument(product != null, "Product argument is required.");
        Preconditions.checkArgument(quantity != null, "Quantity argument is required.");

        Entity position = createPosition(product, quantity, givenQuantity, givenUnit, conversion, price, batch, productionDate,
                expirationDate, resource, storageLocation, palletNumber, typeOfPallet, additionalCode, isWaste);

        positions.add(position);

        return this;
    }

    /**
     * Creates position with given field values (with the same base and given unit)
     *
     * @param product
     * @param quantity
     * @param price
     * @param batch
     * @param expirationDate
     * @param productionDate
     * @param resource
     * @return Created position entity
     */
    public Entity createPosition(final Entity product, final BigDecimal quantity, final BigDecimal price, final String batch,
                                 final Date productionDate, final Date expirationDate, final Entity resource) {
        DataDefinition positionDD = dataDefinitionService.get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                MaterialFlowResourcesConstants.MODEL_POSITION);

        Entity position = positionDD.create();

        position.setField(PositionFields.PRODUCT, product);
        position.setField(PositionFields.QUANTITY, quantity);
        position.setField(PositionFields.GIVEN_QUANTITY, quantity);
        position.setField(PositionFields.GIVEN_UNIT, product.getStringField(ProductFields.UNIT));
        position.setField(PositionFields.PRICE, price);
        position.setField(PositionFields.BATCH, batch);
        position.setField(PositionFields.PRODUCTION_DATE, productionDate);
        position.setField(PositionFields.EXPIRATION_DATE, expirationDate);
        position.setField(PositionFields.RESOURCE, resource);

        return position;
    }

    /**
     * Creates position with given field values (with different base unit and given unit)
     *
     * @param product
     * @param quantity
     * @param givenQuantity
     * @param givenUnit
     * @param price
     * @param batch
     * @param expirationDate
     * @param productionDate
     * @param resource
     * @return Created position entity
     */
    public Entity createPosition(final Entity product, final BigDecimal quantity, final BigDecimal givenQuantity,
                                 final String givenUnit, final BigDecimal conversion, final BigDecimal price, final String batch,
                                 final Date productionDate, final Date expirationDate, final Entity resource) {
        Entity position = createPosition(product, quantity, price, batch, productionDate, expirationDate, resource);

        position.setField(PositionFields.CONVERSION, conversion);
        position.setField(PositionFields.GIVEN_QUANTITY, givenQuantity);
        position.setField(PositionFields.GIVEN_UNIT, givenUnit);

        return position;
    }

    public Entity createPosition(final Entity product, final BigDecimal quantity, final BigDecimal givenQuantity,
                                 final String givenUnit, final BigDecimal conversion, final BigDecimal price, final String batch,
                                 final Date productionDate, final Date expirationDate, final Entity resource, final Entity storageLocation,
                                 final Entity palletNumber, final String typeOfPallet, final Entity additionalCode, final boolean isWaste) {
        Entity position = createPosition(product, quantity, givenQuantity, givenUnit, conversion, price, batch, productionDate,
                expirationDate, resource);

        position.setField(PositionFields.STORAGE_LOCATION, storageLocation);
        position.setField(PositionFields.PALLET_NUMBER, palletNumber);
        position.setField(PositionFields.TYPE_OF_PALLET, typeOfPallet);
        position.setField(PositionFields.ADDITIONAL_CODE, additionalCode);
        position.setField(PositionFields.WASTE, isWaste);

        return position;
    }

    /**
     * Add previously created position to document
     *
     * @param position
     * @return DocumentBuilder.this
     */
    public DocumentBuilder addPosition(final Entity position) {
        Preconditions.checkArgument(position != null, "Position argument is required.");

        positions.add(position);

        return this;
    }

    public DocumentBuilder setAccepted() {
        document.setField(DocumentFields.STATE, DocumentState.ACCEPTED.getStringValue());

        return this;
    }

    /**
     * Use this method to set document fields added by any extending plugins.
     *
     * @param field
     *            field name
     * @param value
     *            field value
     * @return this builder
     */
    public DocumentBuilder setField(String field, Object value) {
        document.setField(field, value);

        return this;
    }

    /**
     * Use this method to get document type.
     */
    public DocumentType getDocumentType() {
        return DocumentType.parseString(document.getStringField(DocumentFields.TYPE));
    }

    private Entity buildWithInvalidStrategy(Consumer<BuildContext> strategy) {
        DataDefinition documentDD = dataDefinitionService.get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                MaterialFlowResourcesConstants.MODEL_DOCUMENT);

        Entity savedDocument = documentDD.save(document);

        positions.forEach(p -> p.setField(PositionFields.DOCUMENT, savedDocument));

        savedDocument.setField(DocumentFields.POSITIONS, positions);
        final ArrayList<Entity> invalidPositions = Lists.newArrayList();
        if (savedDocument.isValid()) {
            if (DocumentState.ACCEPTED.getStringValue().equals(savedDocument.getStringField(DocumentFields.STATE))) {
                resourceManagementService.createResources(savedDocument);

                if (receiptDocumentForReleaseHelper.buildConnectedPZDocument(savedDocument)) {
                    receiptDocumentForReleaseHelper.tryBuildConnectedPZDocument(savedDocument, false);
                }
            } else {
                positions.forEach(p -> {
                    p = p.getDataDefinition().save(p);
                    if (!p.isValid()) {
                        invalidPositions.add(p);
                        savedDocument.setNotValid();
                        p.getGlobalErrors()
                                .forEach(e -> savedDocument.addGlobalError(e.getMessage(), e.getAutoClose(), e.getVars()));
                        p.getErrors().values()
                                .forEach(e -> savedDocument.addGlobalError(e.getMessage(), e.getAutoClose(), e.getVars()));
                    }
                });
            }
        }

        if (!savedDocument.isValid()) {
            strategy.accept(new BuildContext(savedDocument, invalidPositions));
        }

        return savedDocument;

    }

    /**
     * Save document in database and creates resources if document is accepted.
     *
     * @return Created document entity.
     */
    public Entity build() {
        return buildWithInvalidStrategy((buildContext) -> TransactionAspectSupport.currentTransactionStatus().setRollbackOnly());
    }

    public Entity buildWithEntityRuntimeException() {
        return buildWithInvalidStrategy((buildContext) -> {
            throw new DocumentBuildException(buildContext.savedDocument, buildContext.invalidPositions);
        });
    }

    private static class BuildContext {

        private final Entity savedDocument;

        private final List<Entity> invalidPositions;

        private BuildContext(Entity savedDocument, List<Entity> invalidPositions) {
            this.savedDocument = Objects.requireNonNull(savedDocument);
            this.invalidPositions = Objects.requireNonNull(invalidPositions);
        }
    }

    public Entity createDocument(UserService userService) {
        Entity newDocument = createDocument();
        newDocument.setField(DocumentFields.USER, userService.getCurrentUserEntity().getId());
        return newDocument;
    }

    private Entity createDocument(final Entity user) {
        Entity newDocument = createDocument();
        newDocument.setField(DocumentFields.USER, user.getId());
        return newDocument;
    }

    private Entity createDocument() {
        DataDefinition documentDD = dataDefinitionService.get(MaterialFlowResourcesConstants.PLUGIN_IDENTIFIER,
                MaterialFlowResourcesConstants.MODEL_DOCUMENT);

        Entity newDocument = documentDD.create();

        newDocument.setField(DocumentFields.TIME, new Date());
        newDocument.setField(DocumentFields.STATE, DocumentState.DRAFT.getStringValue());
        newDocument.setField(DocumentFields.POSITIONS, Lists.newArrayList());

        return newDocument;
    }

}
