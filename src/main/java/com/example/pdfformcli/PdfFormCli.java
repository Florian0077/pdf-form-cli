package com.example.pdfformcli;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.*;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.action.PdfAction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.*;
import java.util.List;
import java.util.Map;

public class PdfFormCli {

    public static void main(String[] args) {
        if (args.length != 3 && args.length != 5) {
            System.out.println(
                    "Usage: java PdfFormCli <inputPdfPath> <outputPdfPath> <jsonFieldsPath> [defaultTextColor] [defaultBackgroundColor]");
            System.exit(1);
        }

        String inputPdfPath = args[0];
        String outputPdfPath = args[1];
        String jsonFieldsPath = args[2];

        String defaultTextColorStr = null;
        String defaultBgColorStr = null;

        if (args.length == 5) {
            defaultTextColorStr = args[3];
            defaultBgColorStr = args[4];
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> fields = mapper.readValue(new File(jsonFieldsPath),
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputPdfPath), new PdfWriter(outputPdfPath));
            PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);

            for (Map<String, Object> field : fields) {
                String type = (String) field.get("type");
                int page = ((Number) field.get("page")).intValue();
                Map<String, Object> position = (Map<String, Object>) field.get("position");
                String fieldName = (String) field.get("name");

                PdfPage pdfPage = pdfDoc.getPage(page + 1);
                Rectangle rect = new Rectangle(
                        toFloat(position.get("x")),
                        toFloat(position.get("y")),
                        toFloat(position.get("width")),
                        toFloat(position.get("height")));

                switch (type) {
                    case "checkbox":
                        PdfButtonFormField checkbox = PdfFormField.createCheckBox(pdfDoc, rect, fieldName, "Off");

                        // Appliquer la couleur de fond
                        applyBackgroundColor(checkbox, field, defaultBgColorStr);

                        form.addField(checkbox, pdfPage);
                        break;

                    case "text":
                        PdfTextFormField textField = PdfFormField.createText(pdfDoc, rect, fieldName, "");

                        // Appliquer les couleurs
                        applyTextColor(textField, field, defaultTextColorStr);
                        applyBackgroundColor(textField, field, defaultBgColorStr);

                        form.addField(textField, pdfPage);
                        break;

                    case "date":
                        PdfTextFormField dateField = createCalendarDateField(pdfDoc, form, pdfPage, rect, fieldName);

                        // Appliquer les couleurs
                        applyTextColor(dateField, field, defaultTextColorStr);
                        applyBackgroundColor(dateField, field, defaultBgColorStr);

                        form.addField(dateField, pdfPage);
                        break;

                    case "multiline":
                        PdfTextFormField multilineField = PdfFormField.createMultilineText(pdfDoc, rect, fieldName, "");

                        // Appliquer les couleurs
                        applyTextColor(multilineField, field, defaultTextColorStr);
                        applyBackgroundColor(multilineField, field, defaultBgColorStr);

                        form.addField(multilineField, pdfPage);
                        break;

                    case "signature":
                        PdfSignatureFormField signatureField = PdfSignatureFormField.createSignature(pdfDoc, rect);
                        signatureField.setFieldName(fieldName);

                        // Appliquer la couleur de fond
                        applyBackgroundColor(signatureField, field, defaultBgColorStr);

                        form.addField(signatureField, pdfPage);
                        break;

                    default:
                        throw new IllegalArgumentException("Unsupported field type: " + type);
                }
            }

            pdfDoc.close();
            System.out.println("PDF with form fields created successfully: " + outputPdfPath);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static PdfTextFormField createCalendarDateField(PdfDocument pdfDoc, PdfAcroForm form, PdfPage page,
            Rectangle rect, String fieldName) {
        // Créer un champ texte pour la date
        PdfTextFormField dateField = PdfFormField.createText(pdfDoc, rect, fieldName, "");

        // JavaScript pour ouvrir le sélecteur de date lorsque le champ reçoit le focus
        String js = "var cDate = app.pickDate(); " +
                "if (cDate != null) event.target.value = util.printd('dd/mm/yy', cDate);";

        // Remplacer PdfName.FO par PdfName.Fo
        dateField.setAdditionalAction(PdfName.Fo, PdfAction.createJavaScript(js));

        // Ajouter des scripts pour gérer le formatage de la date lors de la saisie
        dateField.setAdditionalAction(PdfName.K, PdfAction.createJavaScript("AFDate_KeystrokeEx('dd/mm/yy');"));
        dateField.setAdditionalAction(PdfName.F, PdfAction.createJavaScript("AFDate_FormatEx('dd/mm/yy');"));

        // Alignement centré du texte
        dateField.setJustification(PdfFormField.ALIGN_CENTER);

        return dateField;
    }

    private static float toFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        throw new IllegalArgumentException("Cannot convert to float: " + value);
    }

    private static Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) {
            throw new IllegalArgumentException("Color string is null or empty");
        }
        if (colorStr.startsWith("#")) {
            colorStr = colorStr.substring(1);
        }
        if (colorStr.length() == 6) {
            int r = Integer.parseInt(colorStr.substring(0, 2), 16);
            int g = Integer.parseInt(colorStr.substring(2, 4), 16);
            int b = Integer.parseInt(colorStr.substring(4, 6), 16);
            return new DeviceRgb(r, g, b);
        } else {
            throw new IllegalArgumentException("Invalid color format: " + colorStr);
        }
    }

    private static void applyTextColor(PdfFormField field, Map<String, Object> fieldData, String defaultColorStr) {
        try {
            Color textColor = null;
            if (fieldData.containsKey("textColor")) {
                textColor = parseColor((String) fieldData.get("textColor"));
            } else if (defaultColorStr != null) {
                textColor = parseColor(defaultColorStr);
            }
            if (textColor != null) {
                field.setColor(textColor);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur lors de la conversion de la couleur du texte pour le champ "
                    + field.getFieldName() + ": " + e.getMessage());
        }
    }

    private static void applyBackgroundColor(PdfFormField field, Map<String, Object> fieldData,
            String defaultColorStr) {
        try {
            Color bgColor = null;
            if (fieldData.containsKey("backgroundColor")) {
                bgColor = parseColor((String) fieldData.get("backgroundColor"));
            } else if (defaultColorStr != null) {
                bgColor = parseColor(defaultColorStr);
            }
            if (bgColor != null) {
                field.setBackgroundColor(bgColor);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur lors de la conversion de la couleur de fond pour le champ "
                    + field.getFieldName() + ": " + e.getMessage());
        }
    }
}
