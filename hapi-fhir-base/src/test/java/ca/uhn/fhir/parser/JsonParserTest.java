package ca.uhn.fhir.parser;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;

import net.sf.json.JSON;
import net.sf.json.JSONSerializer;

import org.apache.commons.io.IOUtils;
import org.hamcrest.core.IsNot;
import org.hamcrest.core.StringContains;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.BundleEntry;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.TagList;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Extension;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.model.dstu.composite.AddressDt;
import ca.uhn.fhir.model.dstu.composite.HumanNameDt;
import ca.uhn.fhir.model.dstu.composite.NarrativeDt;
import ca.uhn.fhir.model.dstu.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu.resource.Conformance;
import ca.uhn.fhir.model.dstu.resource.Conformance.RestResource;
import ca.uhn.fhir.model.dstu.resource.DiagnosticReport;
import ca.uhn.fhir.model.dstu.resource.Observation;
import ca.uhn.fhir.model.dstu.resource.Organization;
import ca.uhn.fhir.model.dstu.resource.Patient;
import ca.uhn.fhir.model.dstu.resource.Specimen;
import ca.uhn.fhir.model.dstu.resource.ValueSet;
import ca.uhn.fhir.model.dstu.resource.ValueSet.Define;
import ca.uhn.fhir.model.dstu.resource.ValueSet.DefineConcept;
import ca.uhn.fhir.model.dstu.valueset.AddressUseEnum;
import ca.uhn.fhir.model.dstu.valueset.NarrativeStatusEnum;
import ca.uhn.fhir.model.primitive.DecimalDt;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.model.primitive.XhtmlDt;
import ca.uhn.fhir.narrative.INarrativeGenerator;

public class JsonParserTest {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(JsonParserTest.class);
	private static FhirContext ourCtx;

	@Test
	public void testEncodingNullExtension() {
		Patient p = new Patient();
		p.addUndeclaredExtension(new ExtensionDt(false, "http://foo#bar"));
		String str = new FhirContext().newJsonParser().encodeResourceToString(p);
		
		assertEquals("{\"resourceType\":\"Patient\",\"extension\":[{\"url\":\"http://foo#bar\"}]}", str);
	}

	@Test
	public void testTagList() {
		
		//@formatter:off
		String tagListStr = "{\n" + 
				"  \"resourceType\" : \"TagList\", " + 
				"  \"category\" : [" + 
				"    { " + 
				"      \"term\" : \"term0\", " + 
				"      \"label\" : \"label0\", " + 
				"      \"scheme\" : \"scheme0\" " + 
				"    }," +
				"    { " + 
				"      \"term\" : \"term1\", " + 
				"      \"label\" : \"label1\", " + 
				"      \"scheme\" : null " + 
				"    }," +
				"    { " + 
				"      \"term\" : \"term2\", " + 
				"      \"label\" : \"label2\" " + 
				"    }" +
				"  ] " + 
				"}";
		//@formatter:on
		
		TagList tagList = new FhirContext().newJsonParser().parseTagList(tagListStr);
		assertEquals(3, tagList.size());
		assertEquals("term0", tagList.get(0).getTerm());
		assertEquals("label0", tagList.get(0).getLabel());
		assertEquals("scheme0", tagList.get(0).getScheme());
		assertEquals("term1", tagList.get(1).getTerm());
		assertEquals("label1", tagList.get(1).getLabel());
		assertEquals(null, tagList.get(1).getScheme());
		assertEquals("term2", tagList.get(2).getTerm());
		assertEquals("label2", tagList.get(2).getLabel());
		assertEquals(null, tagList.get(2).getScheme());
		
		/*
		 * Encode
		 */

		//@formatter:off
		String expected = "{" + 
				"\"resourceType\":\"TagList\"," + 
				"\"category\":[" + 
				"{" + 
				"\"term\":\"term0\"," + 
				"\"label\":\"label0\"," + 
				"\"scheme\":\"scheme0\"" + 
				"}," +
				"{" + 
				"\"term\":\"term1\"," + 
				"\"label\":\"label1\"" + 
				"}," +
				"{" + 
				"\"term\":\"term2\"," + 
				"\"label\":\"label2\"" + 
				"}" +
				"]" + 
				"}";
		//@formatter:on
		
		String encoded = new FhirContext().newJsonParser().encodeTagListToString(tagList);
		assertEquals(expected,encoded);

	}
	
	@Test
	public void testEncodeBundleCategory() {

		Bundle b = new Bundle();
		BundleEntry e = b.addEntry();
		e.setResource(new Patient());
		e.addCategory().setLabel("label").setTerm("term").setScheme("scheme");
		
		String val = new FhirContext().newJsonParser().setPrettyPrint(false).encodeBundleToString(b);
		ourLog.info(val);

		assertThat(val, StringContains.containsString("\"category\":[{\"term\":\"term\",\"label\":\"label\",\"scheme\":\"scheme\"}]"));
		
		b = new FhirContext().newJsonParser().parseBundle(val);
		assertEquals(1,b.getEntries().size());
		assertEquals(1,b.getEntries().get(0).getCategories().size());
		assertEquals("term", b.getEntries().get(0).getCategories().get(0).getTerm());
		assertEquals("label", b.getEntries().get(0).getCategories().get(0).getLabel());
		assertEquals("scheme", b.getEntries().get(0).getCategories().get(0).getScheme());
		assertNull(b.getEntries().get(0).getResource());

	}

	
	@Test
	public void testEncodeContainedResources() throws IOException {

		String msg = IOUtils.toString(XmlParser.class.getResourceAsStream("/contained-diagnosticreport.xml"));
		IParser p = ourCtx.newXmlParser();
		DiagnosticReport res = p.parseResource(DiagnosticReport.class, msg);

		String encoded = ourCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(res);
		ourLog.info(encoded);

	}
	
	
	@Test
	public void testEncodeContainedResourcesMore() {

		DiagnosticReport rpt = new DiagnosticReport();
		Specimen spm = new Specimen();
		spm.getText().setDiv("AAA");
		rpt.addSpecimen().setResource(spm);

		IParser p = new FhirContext(DiagnosticReport.class).newJsonParser().setPrettyPrint(true);
		String str = p.encodeResourceToString(rpt);

		ourLog.info(str);
		assertThat(str, StringContains.containsString("<div>AAA</div>"));
		String substring = "\"resource\":\"#";
		assertThat(str, StringContains.containsString(substring));

		int idx = str.indexOf(substring) + substring.length();
		int idx2 = str.indexOf('"', idx + 1);
		String id = str.substring(idx, idx2);
		assertThat(str, StringContains.containsString("\"id\":\"" + id + "\""));
		assertThat(str, IsNot.not(StringContains.containsString("<?xml version='1.0'?>")));

	}

	@Test
	public void testEncodeDeclaredExtensionWithAddressContent() {
		IParser parser = new FhirContext().newJsonParser();

		MyPatientWithOneDeclaredAddressExtension patient = new MyPatientWithOneDeclaredAddressExtension();
		patient.addAddress().setUse(AddressUseEnum.HOME);
		patient.setFoo(new AddressDt().addLine("line1"));

		String val = parser.encodeResourceToString(patient);
		ourLog.info(val);
		assertThat(val, StringContains.containsString("\"extension\":[{\"url\":\"urn:foo\",\"valueAddress\":{\"line\":[\"line1\"]}}]"));

		MyPatientWithOneDeclaredAddressExtension actual = parser.parseResource(MyPatientWithOneDeclaredAddressExtension.class, val);
		assertEquals(AddressUseEnum.HOME, patient.getAddressFirstRep().getUse().getValueAsEnum());
		AddressDt ref = actual.getFoo();
		assertEquals("line1", ref.getLineFirstRep().getValue());

	}
	
	
	@Test
	public void testEncodeDeclaredExtensionWithResourceContent() {
		IParser parser = new FhirContext().newJsonParser();

		MyPatientWithOneDeclaredExtension patient = new MyPatientWithOneDeclaredExtension();
		patient.addAddress().setUse(AddressUseEnum.HOME);
		patient.setFoo(new ResourceReferenceDt("Organization/123"));

		String val = parser.encodeResourceToString(patient);
		ourLog.info(val);
		assertThat(val, StringContains.containsString("\"extension\":[{\"url\":\"urn:foo\",\"valueResource\":{\"resource\":\"Organization/123\"}}]"));

		MyPatientWithOneDeclaredExtension actual = parser.parseResource(MyPatientWithOneDeclaredExtension.class, val);
		assertEquals(AddressUseEnum.HOME, patient.getAddressFirstRep().getUse().getValueAsEnum());
		ResourceReferenceDt ref = actual.getFoo();
		assertEquals("Organization/123", ref.getResourceId().getValue());

	}
	
	
	@Test
	public void testEncodeExt() throws Exception {

		ValueSet valueSet = new ValueSet();
		Define define = valueSet.getDefine();
		DefineConcept code = define.addConcept();
		code.setCode("someCode");
		code.setDisplay("someDisplay");
		code.addUndeclaredExtension(false, "urn:alt", new StringDt("alt name"));

		String encoded = new FhirContext().newJsonParser().encodeResourceToString(valueSet);
		ourLog.info(encoded);

	}

	
	@Test
	public void testEncodeExtensionWithResourceContent() {
		IParser parser = new FhirContext().newJsonParser();

		Patient patient = new Patient();
		patient.addAddress().setUse(AddressUseEnum.HOME);
		patient.addUndeclaredExtension(false, "urn:foo", new ResourceReferenceDt("Organization/123"));

		String val = parser.encodeResourceToString(patient);
		ourLog.info(val);
		assertThat(val, StringContains.containsString("\"extension\":[{\"url\":\"urn:foo\",\"valueResource\":{\"resource\":\"Organization/123\"}}]"));

		Patient actual = parser.parseResource(Patient.class, val);
		assertEquals(AddressUseEnum.HOME, patient.getAddressFirstRep().getUse().getValueAsEnum());
		List<ExtensionDt> ext = actual.getUndeclaredExtensionsByUrl("urn:foo");
		assertEquals(1, ext.size());
		ResourceReferenceDt ref = (ResourceReferenceDt) ext.get(0).getValue();
		assertEquals("Organization/123", ref.getReference().getValue());

	}

	
	@Test
	public void testEncodeInvalidChildGoodException() {
		Observation obs = new Observation();
		obs.setValue(new DecimalDt(112.22));

		IParser p = new FhirContext(Observation.class).newJsonParser();

		try {
			p.encodeResourceToString(obs);
		} catch (DataFormatException e) {
			assertThat(e.getMessage(), StringContains.containsString("PeriodDt"));
		}
	}
	
	@Test
	public void testEncodeResourceRef() throws DataFormatException {

		Patient patient = new Patient();
		patient.setManagingOrganization(new ResourceReferenceDt());

		IParser p = new FhirContext().newJsonParser();
		String str = p.encodeResourceToString(patient);
		assertThat(str, IsNot.not(StringContains.containsString("managingOrganization")));

		patient.setManagingOrganization(new ResourceReferenceDt("Organization/123"));
		str = p.encodeResourceToString(patient);
		assertThat(str, StringContains.containsString("\"managingOrganization\":{\"resource\":\"Organization/123\"}"));

		Organization org = new Organization();
		org.addIdentifier().setSystem("foo").setValue("bar");
		patient.setManagingOrganization(new ResourceReferenceDt(org));
		str = p.encodeResourceToString(patient);
		assertThat(str, StringContains.containsString("\"contained\":[{\"resourceType\":\"Organization\""));

	}

	@Test
	public void testEncodeUndeclaredExtensionWithAddressContent() {
		IParser parser = new FhirContext().newJsonParser();

		Patient patient = new Patient();
		patient.addAddress().setUse(AddressUseEnum.HOME);
		patient.addUndeclaredExtension(false, "urn:foo", new AddressDt().addLine("line1"));

		String val = parser.encodeResourceToString(patient);
		ourLog.info(val);
		assertThat(val, StringContains.containsString("\"extension\":[{\"url\":\"urn:foo\",\"valueAddress\":{\"line\":[\"line1\"]}}]"));

		MyPatientWithOneDeclaredAddressExtension actual = parser.parseResource(MyPatientWithOneDeclaredAddressExtension.class, val);
		assertEquals(AddressUseEnum.HOME, patient.getAddressFirstRep().getUse().getValueAsEnum());
		AddressDt ref = actual.getFoo();
		assertEquals("line1", ref.getLineFirstRep().getValue());

	}

	@Test
	public void testExtensionOnComposite() throws Exception {

		Patient patient = new Patient();

		HumanNameDt name = patient.addName();
		name.addFamily().setValue("Shmoe");
		HumanNameDt given = name.addGiven("Joe");
		ExtensionDt ext2 = new ExtensionDt(false, "http://examples.com#givenext", new StringDt("Hello"));
		given.addUndeclaredExtension(ext2);
		String enc = new FhirContext().newJsonParser().encodeResourceToString(patient);
		ourLog.info(enc);
		//@formatter:off
		assertThat(enc, containsString(("{" + 
				"    \"resourceType\":\"Patient\"," + 
				"    \"name\":[" + 
				"        {" + 
				"            \"family\":[" + 
				"                \"Shmoe\"" + 
				"            ]," + 
				"            \"given\":[" + 
				"                \"Joe\"" + 
				"            ]" + 
				"        }" + 
				"    ]," + 
				"    \"_name\":[" + 
				"        {" + 
				"            \"extension\":[" + 
				"                {" + 
				"                    \"url\":\"http://examples.com#givenext\"," + 
				"                    \"valueString\":\"Hello\"" + 
				"                }" + 
				"            ]" + 
				"        }" + 
				"    ]" + 
				"}").replaceAll(" +", "")));
		//@formatter:on

		IParser newJsonParser = new FhirContext().newJsonParser();
		StringReader reader = new StringReader(enc);
		Patient parsed = newJsonParser.parseResource(Patient.class, reader);

		ourLog.info(new FhirContext().newXmlParser().setPrettyPrint(true).encodeResourceToString(parsed));

		assertEquals(1, parsed.getNameFirstRep().getUndeclaredExtensionsByUrl("http://examples.com#givenext").size());
		ExtensionDt ext = parsed.getNameFirstRep().getUndeclaredExtensionsByUrl("http://examples.com#givenext").get(0);
		assertEquals("Hello", ext.getValueAsPrimitive().getValue());

	}

	@Test
	public void testExtensionOnPrimitive() throws Exception {

		Patient patient = new Patient();

		HumanNameDt name = patient.addName();
		StringDt family = name.addFamily();
		family.setValue("Shmoe");

		ExtensionDt ext2 = new ExtensionDt(false, "http://examples.com#givenext", new StringDt("Hello"));
		family.addUndeclaredExtension(ext2);
		String enc = new FhirContext().newJsonParser().encodeResourceToString(patient);
		ourLog.info(enc);
		//@formatter:off
		assertThat(enc, containsString(("{\n" + 
				"    \"resourceType\":\"Patient\",\n" + 
				"    \"name\":[\n" + 
				"        {\n" + 
				"            \"family\":[\n" + 
				"                \"Shmoe\"\n" + 
				"            ],\n" + 
				"            \"_family\":[\n" + 
				"                {\n" + 
				"                    \"extension\":[\n" + 
				"                        {\n" + 
				"                            \"url\":\"http://examples.com#givenext\",\n" + 
				"                            \"valueString\":\"Hello\"\n" + 
				"                        }\n" + 
				"                    ]\n" + 
				"                }\n" + 
				"            ]\n" + 
				"        }\n" + 
				"    ]\n" + 
				"}").replace("\n", "").replaceAll(" +", "")));
		//@formatter:on

		Patient parsed = new FhirContext().newJsonParser().parseResource(Patient.class, new StringReader(enc));
		assertEquals(1, parsed.getNameFirstRep().getFamilyFirstRep().getUndeclaredExtensionsByUrl("http://examples.com#givenext").size());
		ExtensionDt ext = parsed.getNameFirstRep().getFamilyFirstRep().getUndeclaredExtensionsByUrl("http://examples.com#givenext").get(0);
		assertEquals("Hello", ext.getValueAsPrimitive().getValue());

	}

	@Test
	public void testNarrativeGeneration() throws DataFormatException, IOException {

		Patient patient = new Patient();
		patient.addName().addFamily("Smith");
		Organization org = new Organization();
		patient.getManagingOrganization().setResource(org);

		INarrativeGenerator gen = mock(INarrativeGenerator.class);
		XhtmlDt xhtmlDt = new XhtmlDt("<div>help</div>");
		NarrativeDt nar = new NarrativeDt(xhtmlDt, NarrativeStatusEnum.GENERATED);
		when(gen.generateNarrative(eq("http://hl7.org/fhir/profiles/Patient"), eq(patient))).thenReturn(nar);

		FhirContext context = new FhirContext();
		context.setNarrativeGenerator(gen);
		IParser p = context.newJsonParser();
		p.encodeResourceToWriter(patient, new OutputStreamWriter(System.out));
		String str = p.encodeResourceToString(patient);

		ourLog.info(str);

		assertThat(str, StringContains.containsString(",\"text\":{\"status\":\"generated\",\"div\":\"<div>help</div>\"},"));
	}

	@Test
	public void testParseBundle() throws DataFormatException, IOException {

		String msg = IOUtils.toString(XmlParser.class.getResourceAsStream("/atom-document-large.json"));
		IParser p = ourCtx.newJsonParser();
		Bundle bundle = p.parseBundle(msg);

		String encoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeBundleToString(bundle);
		ourLog.info(encoded);

		assertEquals("http://fhir.healthintersections.com.au/open/DiagnosticReport/_search?_format=application/json+fhir&search-id=46d5f0e7-9240-4d4f-9f51-f8ac975c65&search-sort=_id", bundle
				.getLinkSelf().getValue());
		assertEquals("urn:uuid:0b754ff9-03cf-4322-a119-15019af8a3", bundle.getBundleId().getValue());

		BundleEntry entry = bundle.getEntries().get(0);
		assertEquals("http://fhir.healthintersections.com.au/open/DiagnosticReport/101", entry.getId().getValue());
		assertEquals("http://fhir.healthintersections.com.au/open/DiagnosticReport/101/_history/1", entry.getLinkSelf().getValue());
		assertEquals("2014-03-10T11:55:59Z", entry.getUpdated().getValueAsString());

		DiagnosticReport res = (DiagnosticReport) entry.getResource();
		assertEquals("Complete Blood Count", res.getName().getText().getValue());

	}

	/**
	 * This sample has extra elements in <searchParam> that are not actually a part of the spec any more..
	 */
	@Test
	public void testParseFuroreMetadataWithExtraElements() throws IOException {
		String msg = IOUtils.toString(JsonParserTest.class.getResourceAsStream("/furore-conformance.json"));

		IParser p = ourCtx.newJsonParser();
		Conformance conf = p.parseResource(Conformance.class, msg);
		RestResource res = conf.getRestFirstRep().getResourceFirstRep();
		assertEquals("_id", res.getSearchParam().get(1).getName().getValue());
	}

	@Test
	public void testParseWithContained() throws DataFormatException, IOException {

		String msg = IOUtils.toString(XmlParser.class.getResourceAsStream("/diagnostic-report.json"));
		IParser p = ourCtx.newJsonParser();
		// ourLog.info("Reading in message: {}", msg);
		DiagnosticReport res = p.parseResource(DiagnosticReport.class, msg);

		String encoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(res);
		ourLog.info(encoded);

		ResourceReferenceDt reference = res.getResult().get(1);
		Observation obs = (Observation) reference.getResource();

		assertEquals("789-8", obs.getName().getCoding().get(0).getCode().getValue());
	}

	@BeforeClass
	public static void beforeClass() {
		ourCtx = new FhirContext();
	}
	
	@Test
	public void testParseBundleDeletedEntry() {
		
		//@formatter:off
		String bundleString = 
				"{" + 
					"\"resourceType\":\"Bundle\"," + 
					"\"totalResults\":\"1\"," + 
					"\"entry\":[" +
						"{" + 
							"\"deleted\":\"2012-05-29T23:45:32+00:00\"," + 
							"\"id\":\"http://fhir.furore.com/fhir/Patient/1\"," + 
							"\"link\":[" +
								"{" + 
									"\"rel\":\"self\"," + 
									"\"href\":\"http://fhir.furore.com/fhir/Patient/1/_history/2\"" + 
								"}" +
							"]" + 
						"}" +
					"]" + 
				"}";
		//@formatter:on
		
		Bundle bundle = ourCtx.newJsonParser().parseBundle(bundleString);
		BundleEntry entry = bundle.getEntries().get(0);
		assertEquals("2012-05-29T23:45:32+00:00", entry.getDeletedAt().getValueAsString());
		assertEquals("http://fhir.furore.com/fhir/Patient/1/_history/2", entry.getLinkSelf().getValue());
		assertEquals("1", entry.getResource().getId().getUnqualifiedId());
		assertEquals("2", entry.getResource().getId().getUnqualifiedVersionId());
		assertEquals(new InstantDt("2012-05-29T23:45:32+00:00"), entry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.DELETED_AT));
		
		// Now encode
		
		ourLog.info(ourCtx.newJsonParser().setPrettyPrint(true).encodeBundleToString(bundle));
		String encoded = ourCtx.newJsonParser().encodeBundleToString(bundle);
		assertEquals(bundleString,encoded);
		
	}
	
	@Test
	public void testSimpleBundleEncode() throws IOException {

		String xmlString = IOUtils.toString(JsonParser.class.getResourceAsStream("/atom-document-large.xml"), Charset.forName("UTF-8"));
		Bundle obs = ourCtx.newXmlParser().parseBundle(xmlString);

		String encoded = ourCtx.newJsonParser().setPrettyPrint(true).encodeBundleToString(obs);
		ourLog.info(encoded);

	}

	@Test
	public void testSimpleParse() throws DataFormatException, IOException {

		String msg = IOUtils.toString(XmlParser.class.getResourceAsStream("/example-patient-general.json"));
		IParser p = ourCtx.newJsonParser();
		// ourLog.info("Reading in message: {}", msg);
		Patient res = p.parseResource(Patient.class, msg);

		assertEquals(2, res.getUndeclaredExtensions().size());
		assertEquals(1, res.getUndeclaredModifierExtensions().size());

		String encoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(res);
		ourLog.info(encoded);

	}

	@Test
	public void testSimpleResourceEncode() throws IOException {

		String xmlString = IOUtils.toString(JsonParser.class.getResourceAsStream("/example-patient-general.xml"), Charset.forName("UTF-8"));
		Patient obs = ourCtx.newXmlParser().parseResource(Patient.class, xmlString);

		List<ExtensionDt> undeclaredExtensions = obs.getContact().get(0).getName().getFamily().get(0).getUndeclaredExtensions();
		ExtensionDt undeclaredExtension = undeclaredExtensions.get(0);
		assertEquals("http://hl7.org/fhir/Profile/iso-21090#qualifier", undeclaredExtension.getUrl().getValue());

		ourCtx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(obs, new OutputStreamWriter(System.out));

		IParser jsonParser = ourCtx.newJsonParser();
		String encoded = jsonParser.encodeResourceToString(obs);
		ourLog.info(encoded);

		String jsonString = IOUtils.toString(JsonParser.class.getResourceAsStream("/example-patient-general.json"));

		JSON expected = JSONSerializer.toJSON(jsonString);
		JSON actual = JSONSerializer.toJSON(encoded.trim());

		ourLog.info("Expected: {}", expected);
		ourLog.info("Actual  : {}", actual);
		assertEquals(expected.toString(), actual.toString());

	}

	@Test
	public void testSimpleResourceEncodeWithCustomType() throws IOException {

		FhirContext fhirCtx = new FhirContext(MyObservationWithExtensions.class);
		String xmlString = IOUtils.toString(JsonParser.class.getResourceAsStream("/example-patient-general.xml"), Charset.forName("UTF-8"));
		MyObservationWithExtensions obs = fhirCtx.newXmlParser().parseResource(MyObservationWithExtensions.class, xmlString);

		assertEquals(0, obs.getAllUndeclaredExtensions().size());
		assertEquals("aaaa", obs.getExtAtt().getContentType().getValue());
		assertEquals("str1", obs.getMoreExt().getStr1().getValue());
		assertEquals("2011-01-02", obs.getModExt().getValueAsString());

		List<ExtensionDt> undeclaredExtensions = obs.getContact().get(0).getName().getFamily().get(0).getUndeclaredExtensions();
		ExtensionDt undeclaredExtension = undeclaredExtensions.get(0);
		assertEquals("http://hl7.org/fhir/Profile/iso-21090#qualifier", undeclaredExtension.getUrl().getValue());

		fhirCtx.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(obs, new OutputStreamWriter(System.out));

		IParser jsonParser = fhirCtx.newJsonParser();
		String encoded = jsonParser.encodeResourceToString(obs);
		ourLog.info(encoded);

		String jsonString = IOUtils.toString(JsonParser.class.getResourceAsStream("/example-patient-general.json"));

		JSON expected = JSONSerializer.toJSON(jsonString);
		JSON actual = JSONSerializer.toJSON(encoded.trim());

		ourLog.info("Expected: {}", expected);
		ourLog.info("Actual  : {}", actual);
		assertEquals(expected.toString(), actual.toString());

	}

	@ResourceDef(name = "Patient")
	public static class MyPatientWithOneDeclaredAddressExtension extends Patient {

		@Child(order = 0, name = "foo")
		@Extension(url = "urn:foo", definedLocally = true, isModifier = false)
		private AddressDt myFoo;

		public AddressDt getFoo() {
			return myFoo;
		}

		public void setFoo(AddressDt theFoo) {
			myFoo = theFoo;
		}

	}

	@ResourceDef(name = "Patient")
	public static class MyPatientWithOneDeclaredExtension extends Patient {

		@Child(order = 0, name = "foo")
		@Extension(url = "urn:foo", definedLocally = true, isModifier = false)
		private ResourceReferenceDt myFoo;

		public ResourceReferenceDt getFoo() {
			return myFoo;
		}

		public void setFoo(ResourceReferenceDt theFoo) {
			myFoo = theFoo;
		}

	}

}
