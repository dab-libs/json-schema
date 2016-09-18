/*
 * Copyright (C) 2011 Everit Kft. (http://www.everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.json.schema;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ObjectSchemaTest {

    private static final JSONObject OBJECTS  = ResourceLoader.DEFAULT.readObj("objecttestcases.json");

    @Test
    public void additionalPropertiesOnEmptyObject() {
        ObjectSchema.builder()
                .schemaOfAdditionalProperties(BooleanSchema.INSTANCE).build()
                .validate(OBJECTS.getJSONObject("emptyObject"));
    }

    @Test
    public void additionalPropertySchema() {
        ObjectSchema subject = ObjectSchema.builder()
                .schemaOfAdditionalProperties(BooleanSchema.INSTANCE)
                .build();
        TestSupport.failureOf(subject)
                .input(OBJECTS.get("additionalPropertySchema"))
                .expectedPointer("#/foo")
                .expect();
        TestSupport.expectFailure(subject, "#/foo", OBJECTS.get("additionalPropertySchema"));
    }

    @Test
    public void maxPropertiesFailure() {
        ObjectSchema subject = ObjectSchema.builder().maxProperties(2).build();
        TestSupport.failureOf(subject)
                .input(OBJECTS.get("maxPropertiesFailure"))
                .expectedPointer("#")
                .expectedKeyword("maxProperties")
                .expect();
    }

    @Test
    public void minPropertiesFailure() {
        ObjectSchema subject = ObjectSchema.builder().minProperties(2).build();
        TestSupport.failureOf(subject)
                .input(OBJECTS.get("minPropertiesFailure"))
                .expectedPointer("#")
                .expectedKeyword("minProperties")
                .expect();
    }

    @Test
    public void multipleAdditionalProperties() {
        ObjectSchema subject = ObjectSchema.builder().additionalProperties(false).build();
        try {
            subject.validate(new JSONObject("{\"a\":true,\"b\":true}"));
            Assert.fail("did not throw exception for multiple additional properties");
        } catch (ValidationException e) {
            Assert.assertEquals("#: 2 schema violations found", e.getMessage());
            Assert.assertEquals(2, e.getCausingExceptions().size());
        }
    }

    @Test
    public void multipleSchemaDepViolation() {
        Schema billingAddressSchema = new StringSchema();
        Schema billingNameSchema = StringSchema.builder().minLength(4).build();
        ObjectSchema subject = ObjectSchema.builder()
                .addPropertySchema("name", new StringSchema())
                .addPropertySchema("credit_card", NumberSchema.builder().build())
                .schemaDependency("credit_card", ObjectSchema.builder()
                        .addPropertySchema("billing_address", billingAddressSchema)
                        .addRequiredProperty("billing_address")
                        .addPropertySchema("billing_name", billingNameSchema)
                        .build())
                .schemaDependency("name", ObjectSchema.builder()
                        .addRequiredProperty("age")
                        .build())
                .build();
        try {
            subject.validate(OBJECTS.get("schemaDepViolation"));
            Assert.fail("did not throw ValidationException");
        } catch (ValidationException e) {
            ValidationException creditCardFailure = e.getCausingExceptions().get(0);
            ValidationException ageFailure = e.getCausingExceptions().get(1);
            // due to schemaDeps being stored in (unsorted) HashMap, the exceptions may need to be swapped
            if (creditCardFailure.getCausingExceptions().size() == 0) {
                ValidationException tmp = creditCardFailure;
                creditCardFailure = ageFailure;
                ageFailure = tmp;
            }
            ValidationException billingAddressFailure = creditCardFailure.getCausingExceptions().get(0);
            Assert.assertEquals("#/billing_address", billingAddressFailure.getPointerToViolation());
            Assert.assertEquals(billingAddressSchema, billingAddressFailure.getViolatedSchema());
            ValidationException billingNameFailure = creditCardFailure
                    .getCausingExceptions().get(1);
            Assert.assertEquals("#/billing_name", billingNameFailure.getPointerToViolation());
            Assert.assertEquals(billingNameSchema, billingNameFailure.getViolatedSchema());
            Assert.assertEquals("#", ageFailure.getPointerToViolation());
            Assert.assertEquals("#: required key [age] not found", ageFailure.getMessage());
        }
    }

    @Test
    public void multipleViolations() {
        Schema subject = ObjectSchema.builder()
                .addPropertySchema("numberProp", new NumberSchema())
                .patternProperty("^string.*", new StringSchema())
                .addPropertySchema("boolProp", BooleanSchema.INSTANCE)
                .addRequiredProperty("boolProp")
                .build();
        try {
            subject.validate(OBJECTS.get("multipleViolations"));
            Assert.fail("did not throw exception for 3 schema violations");
        } catch (ValidationException e) {
            Assert.assertEquals(3, e.getCausingExceptions().size());
            Assert.assertEquals(1, TestSupport.countCauseByJsonPointer(e, "#/numberProp"));
            Assert.assertEquals(1, TestSupport.countCauseByJsonPointer(e, "#"));
            Assert.assertEquals(1, TestSupport.countCauseByJsonPointer(e, "#/stringPatternMatch"));
        }
    }

    @Test
    public void noAdditionalProperties() {
        ObjectSchema subject = ObjectSchema.builder().additionalProperties(false).build();
        TestSupport.expectFailure(subject, "#", OBJECTS.get("propertySchemaViolation"));
    }

    @Test
    public void noProperties() {
        ObjectSchema.builder().build().validate(OBJECTS.get("noProperties"));
    }

    @Test
    public void notRequireObject() {
        ObjectSchema.builder().requiresObject(false).build().validate("foo");
    }

    @Test
    public void patternPropertyOnEmptyObjct() {
        ObjectSchema.builder()
                .patternProperty("b_.*", BooleanSchema.INSTANCE)
                .build().validate(new JSONObject());
    }

    @Test
    public void patternPropertyOverridesAdditionalPropSchema() {
        ObjectSchema.builder()
                .schemaOfAdditionalProperties(new NumberSchema())
                .patternProperty("aa.*", BooleanSchema.INSTANCE)
                .build().validate(OBJECTS.get("patternPropertyOverridesAdditionalPropSchema"));
    }

    @Test
    public void patternPropertyViolation() {
        ObjectSchema subject = ObjectSchema.builder()
                .patternProperty("^b_.*", BooleanSchema.INSTANCE)
                .patternProperty("^s_.*", new StringSchema())
                .build();
        TestSupport.expectFailure(subject, BooleanSchema.INSTANCE, "#/b_1",
                OBJECTS.get("patternPropertyViolation"));
    }

    @Test
    public void patternPropsOverrideAdditionalProps() {
        ObjectSchema.builder()
                .patternProperty("^v.*", EmptySchema.INSTANCE)
                .additionalProperties(false)
                .build().validate(OBJECTS.get("patternPropsOverrideAdditionalProps"));
    }

    @Test
    public void propertyDepViolation() {
        ObjectSchema subject = ObjectSchema.builder()
                .addPropertySchema("ifPresent", NullSchema.INSTANCE)
                .addPropertySchema("mustBePresent", BooleanSchema.INSTANCE)
                .propertyDependency("ifPresent", "mustBePresent")
                .build();
        TestSupport.failureOf(subject)
                .input(OBJECTS.get("propertyDepViolation"))
                .expectedKeyword("dependencies")
                .expect();
    }

    @Test
    public void propertySchemaViolation() {
        ObjectSchema subject = ObjectSchema.builder()
                .addPropertySchema("boolProp", BooleanSchema.INSTANCE).build();
        TestSupport.expectFailure(subject, BooleanSchema.INSTANCE, "#/boolProp",
                OBJECTS.get("propertySchemaViolation"));
    }

    @Test
    public void requiredProperties() {
        ObjectSchema subject = ObjectSchema.builder()
                .addPropertySchema("boolProp", BooleanSchema.INSTANCE)
                .addPropertySchema("nullProp", NullSchema.INSTANCE)
                .addRequiredProperty("boolProp")
                .build();
        TestSupport.failureOf(subject)
                .expectedPointer("#")
                .expectedKeyword("required")
                .input(OBJECTS.get("requiredProperties"))
                .expect();
    }

    @Test
    public void requireObject() {
        TestSupport.expectFailure(ObjectSchema.builder().build(), "#", "foo");
    }

    @Test
    public void schemaDepViolation() {
        Schema billingAddressSchema = new StringSchema();
        ObjectSchema subject = ObjectSchema.builder()
                .addPropertySchema("name", new StringSchema())
                .addPropertySchema("credit_card", NumberSchema.builder().build())
                .schemaDependency("credit_card", ObjectSchema.builder()
                        .addPropertySchema("billing_address", billingAddressSchema)
                        .addRequiredProperty("billing_address")
                        .build())
                .build();
        TestSupport.expectFailure(subject, billingAddressSchema, "#/billing_address",
                OBJECTS.get("schemaDepViolation"));
    }

    @Test(expected = SchemaException.class)
    public void schemaForNoAdditionalProperties() {
        ObjectSchema.builder().additionalProperties(false)
                .schemaOfAdditionalProperties(BooleanSchema.INSTANCE).build();
    }

    @Test
    public void testImmutability() {
        ObjectSchema.Builder builder = ObjectSchema.builder();
        builder.propertyDependency("a", "b");
        builder.schemaDependency("a", BooleanSchema.INSTANCE);
        builder.patternProperty("aaa", BooleanSchema.INSTANCE);
        ObjectSchema schema = builder.build();
        builder.propertyDependency("c", "a");
        builder.schemaDependency("b", BooleanSchema.INSTANCE);
        builder.patternProperty("bbb", BooleanSchema.INSTANCE);
        Assert.assertEquals(1, schema.getPropertyDependencies().size());
        Assert.assertEquals(1, schema.getSchemaDependencies().size());
        Assert.assertEquals(1, schema.getPatternProperties().size());
    }

    @Test
    public void typeFailure() {
        TestSupport.failureOf(ObjectSchema.builder().build())
                .expectedKeyword("type")
                .input("a")
                .expect();
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(ObjectSchema.class)
                .withRedefinedSuperclass()
                .suppress(Warning.STRICT_INHERITANCE)
                .verify();
    }

    @Test
    public void toStringTest() {
        JSONObject rawSchemaJson = ResourceLoader.DEFAULT.readObj("tostring/objectschema.json");
        String actual = SchemaLoader.load(rawSchemaJson).toString();
        assertTrue(ObjectComparator.deepEquals(rawSchemaJson, new JSONObject(actual)));
    }



    @Test @Ignore
    public void toStringNoExplicitType() {

    }
}
