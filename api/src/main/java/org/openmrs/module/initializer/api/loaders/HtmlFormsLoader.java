package org.openmrs.module.initializer.api.loaders;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.FormService;
import org.openmrs.module.htmlformentry.HtmlForm;
import org.openmrs.module.htmlformentry.HtmlFormEntryService;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.initializer.Domain;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.lang.reflect.Method;

@OpenmrsProfile(modules = { "htmlformentry:*" })
public class HtmlFormsLoader extends BaseFileLoader {
	
	public static final String HTML_FORM_TAG = "htmlform";
	
	public static final String FORM_UUID_ATTRIBUTE = "formUuid";
	
	public static final String FORM_NAME_ATTRIBUTE = "formName";
	
	public static final String FORM_DESCRIPTION_ATTRIBUTE = "formDescription";
	
	public static final String FORM_VERSION_ATTRIBUTE = "formVersion";
	
	public static final String FORM_PUBLISHED_ATTRIBUTE = "formPublished";
	
	public static final String FORM_RETIRED_ATTRIBUTE = "formRetired";
	
	public static final String FORM_ENCOUNTER_TYPE_ATTRIBUTE = "formEncounterType";
	
	public static final String HTML_FORM_UUID_ATTRIBUTE = "htmlformUuid";
	
	@Autowired
	private FormService formService;
	
	@Autowired
	private HtmlFormEntryService htmlFormEntryService;
	
	@Override
	protected Domain getDomain() {
		return Domain.HTML_FORMS;
	}
	
	@Override
	protected String getFileExtension() {
		return "xml";
	}
	
	@Override
	protected void load(File file) throws Exception {
		String xmlData = FileUtils.readFileToString(file, "UTF-8");
		// In HFE 5.5.0, a new service method was introduced to save or update an html form from xml
		// If this method is available, use it, otherwise fall back to the legacy implementation here
		try {
			Method method = HtmlFormEntryService.class.getDeclaredMethod("saveHtmlFormFromXml", String.class);
			method.invoke(htmlFormEntryService, xmlData);
		}
		catch (NoSuchMethodException e) {
			loadFromXmlData(xmlData);
		}
	}
	
	/**
	 * Legacy implementation that loads an htmlform from XML. This has been moved and enhanced in the
	 * HFE module
	 */
	protected void loadFromXmlData(String xmlData) throws Exception {
		Document doc = HtmlFormEntryUtil.stringToDocument(xmlData);
		Node htmlFormNode = HtmlFormEntryUtil.findChild(doc, HTML_FORM_TAG);
		
		String formUuid = getAttributeValue(htmlFormNode, FORM_UUID_ATTRIBUTE);
		if (formUuid == null) {
			throw new IllegalArgumentException(FORM_UUID_ATTRIBUTE + " is required");
		}
		Form form = formService.getFormByUuid(formUuid);
		boolean needToSaveForm = false;
		if (form == null) {
			form = new Form();
			form.setUuid(formUuid);
			needToSaveForm = true;
		}
		
		String formName = getAttributeValue(htmlFormNode, FORM_NAME_ATTRIBUTE);
		if (!OpenmrsUtil.nullSafeEquals(form.getName(), formName)) {
			form.setName(formName);
			needToSaveForm = true;
		}
		
		String formDescription = getAttributeValue(htmlFormNode, FORM_DESCRIPTION_ATTRIBUTE);
		if (!OpenmrsUtil.nullSafeEquals(form.getDescription(), formDescription)) {
			form.setDescription(formDescription);
			needToSaveForm = true;
		}
		
		String formVersion = getAttributeValue(htmlFormNode, FORM_VERSION_ATTRIBUTE);
		if (!OpenmrsUtil.nullSafeEquals(form.getVersion(), formVersion)) {
			form.setVersion(formVersion);
			needToSaveForm = true;
		}
		
		Boolean formPublished = "true".equalsIgnoreCase(getAttributeValue(htmlFormNode, FORM_PUBLISHED_ATTRIBUTE));
		if (!OpenmrsUtil.nullSafeEquals(form.getPublished(), formPublished)) {
			form.setPublished(formPublished);
			needToSaveForm = true;
		}
		
		Boolean formRetired = "true".equalsIgnoreCase(getAttributeValue(htmlFormNode, FORM_RETIRED_ATTRIBUTE));
		if (!OpenmrsUtil.nullSafeEquals(form.getRetired(), formRetired)) {
			form.setRetired(formRetired);
			if (formRetired && StringUtils.isBlank(form.getRetireReason())) {
				form.setRetireReason("Retired by Initializer");
			}
			needToSaveForm = true;
		}
		
		String formEncounterType = getAttributeValue(htmlFormNode, FORM_ENCOUNTER_TYPE_ATTRIBUTE);
		EncounterType encounterType = null;
		if (formEncounterType != null) {
			encounterType = HtmlFormEntryUtil.getEncounterType(formEncounterType);
		}
		if (encounterType != null && !OpenmrsUtil.nullSafeEquals(form.getEncounterType(), encounterType)) {
			form.setEncounterType(encounterType);
			needToSaveForm = true;
		}
		
		if (needToSaveForm) {
			formService.saveForm(form);
		}
		
		HtmlForm htmlForm = htmlFormEntryService.getHtmlFormByForm(form);
		boolean needToSaveHtmlForm = false;
		if (htmlForm == null) {
			htmlForm = new HtmlForm();
			htmlForm.setForm(form);
			needToSaveHtmlForm = true;
		}
		
		// if there is a html form uuid specified, make sure the htmlform uuid is set to that value
		String htmlformUuid = getAttributeValue(htmlFormNode, HTML_FORM_UUID_ATTRIBUTE);
		if (StringUtils.isNotBlank(htmlformUuid) && !OpenmrsUtil.nullSafeEquals(htmlformUuid, htmlForm.getUuid())) {
			htmlForm.setUuid(htmlformUuid);
			needToSaveHtmlForm = true;
		}
		
		if (!OpenmrsUtil.nullSafeEquals(htmlForm.getRetired(), formRetired)) {
			htmlForm.setRetired(formRetired);
			if (formRetired && StringUtils.isBlank(htmlForm.getRetireReason())) {
				htmlForm.setRetireReason("Retired by Initializer");
			}
			needToSaveHtmlForm = true;
		}
		
		if (!StringUtils.trimToEmpty(htmlForm.getXmlData()).equals(StringUtils.trimToEmpty(xmlData))) {
			// trim because if the file ends with a newline the db will have trimmed it
			htmlForm.setXmlData(xmlData);
			needToSaveHtmlForm = true;
		}
		if (needToSaveHtmlForm) {
			htmlFormEntryService.saveHtmlForm(htmlForm);
		}
	}
	
	private static String getAttributeValue(Node htmlForm, String attributeName) {
		Node item = htmlForm.getAttributes().getNamedItem(attributeName);
		return item == null ? null : item.getNodeValue();
	}
}
