package org.jumpmind.symmetric.is.ui.views.design;

import static org.apache.commons.lang.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jumpmind.symmetric.is.core.model.Component;
import org.jumpmind.symmetric.is.core.model.ComponentAttributeSetting;
import org.jumpmind.symmetric.is.core.model.Model;
import org.jumpmind.symmetric.is.core.model.ModelAttribute;
import org.jumpmind.symmetric.is.core.model.ModelEntity;
import org.jumpmind.symmetric.is.core.runtime.component.DbWriter;
import org.jumpmind.symmetric.is.ui.common.ApplicationContext;
import org.jumpmind.symmetric.is.ui.common.ButtonBar;
import org.jumpmind.symmetric.is.ui.common.UiUtils;
import org.jumpmind.symmetric.ui.common.IUiPanel;

import com.vaadin.data.Container;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.FieldEvents.TextChangeListener;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Field;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.ColumnGenerator;
import com.vaadin.ui.TableFieldFactory;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

@SuppressWarnings("serial")
public class EditDbWriterPanel extends VerticalLayout implements IUiPanel {

    ApplicationContext context;

    Component component;

    Table table = new Table();

    TextField filterField;

    List<AttributeSettings> attributeSettings = new ArrayList<AttributeSettings>();

    BeanItemContainer<AttributeSettings> container = new BeanItemContainer<AttributeSettings>(
            AttributeSettings.class);

    public EditDbWriterPanel(ApplicationContext context, Component c) {
        this.context = context;
        this.component = c;

        ButtonBar buttonBar = new ButtonBar();
        addComponent(buttonBar);

        filterField = buttonBar.addFilter();
        filterField.addTextChangeListener(new TextChangeListener() {

            @Override
            public void textChange(TextChangeEvent event) {
                filterField.setValue(event.getText());
                updateTable(event.getText());
            }
        });

        addComponent(buttonBar);

        table.setContainerDataSource(container);

        table.setSelectable(true);
        table.setSortEnabled(false);
        table.setImmediate(true);
        table.setSortEnabled(true);
        table.setSizeFull();
        table.addGeneratedColumn("entityName", new ColumnGenerator() {

            @Override
            public Object generateCell(Table source, Object itemId, Object columnId) {
                AttributeSettings setting = (AttributeSettings) itemId;
                Model model = component.getInputModel();
                ModelAttribute attribute = model.getAttributeById(setting.getAttributeId());
                ModelEntity entity = model.getEntityById(attribute.getEntityId());
                return UiUtils.getName(filterField.getValue(), entity.getName());
            }
        });
        table.addGeneratedColumn("attributeName", new ColumnGenerator() {

            @Override
            public Object generateCell(Table source, Object itemId, Object columnId) {
                AttributeSettings setting = (AttributeSettings) itemId;
                Model model = component.getInputModel();
                ModelAttribute attribute = model.getAttributeById(setting.getAttributeId());
                return UiUtils.getName(filterField.getValue(), attribute.getName());
            }
        });
        table.setVisibleColumns(new Object[] { "entityName", "attributeName", "insertEnabled",
                "updateEnabled" });
        table.setColumnWidth("entityName", 250);
        table.setColumnWidth("attributeName", 250);
        table.setColumnHeaders(new String[] { "Entity Name", "Attribute Name", "Insert Enabled",
                "Update Enabled" });
        table.setColumnExpandRatio("value", 1);
        table.setTableFieldFactory(new EditFieldFactory());
        table.setEditable(true);
        addComponent(table);
        setExpandRatio(table, 1.0f);

        if (component.getInputModel() != null) {

            for (ModelEntity entity : component.getInputModel().getModelEntities()) {
                for (ModelAttribute attr : entity.getModelAttributes()) {
                    ComponentAttributeSetting insert = component.getSingleAttributeSetting(
                            attr.getId(), DbWriter.ATTRIBUTE_INSERT_ENABLED);
                    ComponentAttributeSetting update = component.getSingleAttributeSetting(
                            attr.getId(), DbWriter.ATTRIBUTE_UPDATE_ENABLED);
                    boolean insertEnabled = insert != null ? Boolean
                            .parseBoolean(insert.getValue()) : true;
                    boolean updateEnabled = update != null ? Boolean
                            .parseBoolean(update.getValue()) : true;
                    attributeSettings.add(new AttributeSettings(attr.getId(), insertEnabled,
                            updateEnabled));
                }
            }

            Collections.sort(attributeSettings, new Comparator<AttributeSettings>() {
                @Override
                public int compare(AttributeSettings o1, AttributeSettings o2) {
                    Model model = component.getInputModel();
                    ModelAttribute attribute1 = model.getAttributeById(o1.getAttributeId());
                    ModelEntity entity1 = model.getEntityById(attribute1.getEntityId());

                    ModelAttribute attribute2 = model.getAttributeById(o2.getAttributeId());
                    ModelEntity entity2 = model.getEntityById(attribute2.getEntityId());

                    int compare = entity1.getName().compareTo(entity2.getName());
                    if (compare == 0) {
                        compare = attribute1.getName().compareTo(attribute2.getName());
                    }
                    return compare;
                }
            });
        }

        updateTable(null);

    }

    protected void updateTable(String filter) {
        filter = filter != null ? filter.toLowerCase() : null;
        table.removeAllItems();
        for (AttributeSettings attributeSetting : attributeSettings) {
            Model model = component.getInputModel();
            ModelAttribute attribute = model.getAttributeById(attributeSetting.getAttributeId());
            ModelEntity entity = model.getEntityById(attribute.getEntityId());
            if (isBlank(filter) || entity.getName().toLowerCase().contains(filter)
                    || attribute.getName().toLowerCase().contains(filter)) {
                table.addItem(attributeSetting);
            }
        }
    }

    @Override
    public boolean closing() {
        return true;
    }

    @Override
    public void selected() {
    }

    @Override
    public void deselected() {
    }

    class EditFieldFactory implements TableFieldFactory {
        public Field<?> createField(final Container dataContainer, final Object itemId,
                final Object propertyId, com.vaadin.ui.Component uiContext) {
            final AttributeSettings settings = (AttributeSettings) itemId;

            if (propertyId.equals("insertEnabled")) {
                return createCheckBox(settings, DbWriter.ATTRIBUTE_INSERT_ENABLED);
            } else if (propertyId.equals("updateEnabled")) {
                return createCheckBox(settings, DbWriter.ATTRIBUTE_UPDATE_ENABLED);
            } else {
                return null;
            }
        }
    }

    private CheckBox createCheckBox(final AttributeSettings settings, final String key) {
        final CheckBox checkBox = new CheckBox();
        checkBox.setImmediate(true);
        checkBox.addValueChangeListener(new ValueChangeListener() {

            @Override
            public void valueChange(ValueChangeEvent event) {
                ComponentAttributeSetting setting = component.getSingleAttributeSetting(
                        settings.getAttributeId(), key);

                String oldValue = setting == null ? Boolean.TRUE.toString() : setting.getValue();
                if (setting == null) {
                    setting = new ComponentAttributeSetting(settings.getAttributeId(), component
                            .getId(), key, Boolean.TRUE.toString());
                    component.addAttributeSetting(setting);
                }
                setting.setValue(checkBox.getValue().toString());
                if (!oldValue.equals(setting.getValue())) {
                    context.getConfigurationService().save(setting);
                }
            }
        });
        return checkBox;

    }

    public static class AttributeSettings {

        String attributeId;
        boolean insertEnabled;
        boolean updateEnabled;

        public AttributeSettings(String attributeId, boolean insertEnabled, boolean updateEnabled) {
            this.attributeId = attributeId;
            this.insertEnabled = insertEnabled;
            this.updateEnabled = updateEnabled;
        }

        public void setInsertEnabled(boolean insertEnabled) {
            this.insertEnabled = insertEnabled;
        }

        public boolean isInsertEnabled() {
            return insertEnabled;
        }

        public void setUpdateEnabled(boolean updateEnabled) {
            this.updateEnabled = updateEnabled;
        }

        public boolean isUpdateEnabled() {
            return updateEnabled;
        }

        public String getAttributeId() {
            return attributeId;
        }

        public void setAttributeId(String attributeId) {
            this.attributeId = attributeId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AttributeSettings) {
                return attributeId.equals(((AttributeSettings) obj).getAttributeId());
            } else {
                return super.equals(obj);
            }
        }

        @Override
        public int hashCode() {
            return attributeId.hashCode();
        }

    }

}