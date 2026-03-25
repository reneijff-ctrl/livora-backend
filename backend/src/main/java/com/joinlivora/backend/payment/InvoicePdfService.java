package com.joinlivora.backend.payment;

import com.joinlivora.backend.config.PlatformCompanyProperties;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

@Service("invoicePdfService")
@RequiredArgsConstructor
public class InvoicePdfService {

    private final PlatformCompanyProperties companyProperties;

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

    public byte[] generateInvoicePdf(Invoice invoice) throws IOException {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Header: Platform Company Details (from stored data)
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            
            String sellerName = invoice.getSellerName() != null ? invoice.getSellerName() : companyProperties.getName();
            String sellerAddress = invoice.getSellerAddress() != null ? invoice.getSellerAddress() : companyProperties.getAddress();
            String sellerVat = invoice.getSellerVatNumber() != null ? invoice.getSellerVatNumber() : companyProperties.getVatNumber();
            
            leftCell.addElement(new Paragraph(sellerName, TITLE_FONT));
            leftCell.addElement(new Paragraph(sellerAddress, NORMAL_FONT));
            leftCell.addElement(new Paragraph("VAT: " + sellerVat, NORMAL_FONT));
            
            String sellerEmail = invoice.getSellerEmail() != null ? invoice.getSellerEmail() : companyProperties.getEmail();
            leftCell.addElement(new Paragraph(sellerEmail, NORMAL_FONT)); 
            headerTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph p = new Paragraph("INVOICE", TITLE_FONT);
            p.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(p);
            Paragraph p2 = new Paragraph("Number: " + invoice.getInvoiceNumber(), NORMAL_FONT);
            p2.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(p2);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneOffset.UTC);
            Paragraph p3 = new Paragraph("Date: " + formatter.format(invoice.getIssuedAt()), NORMAL_FONT);
            p3.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(p3);
            headerTable.addCell(rightCell);

            document.add(headerTable);
            document.add(new Paragraph("\n"));

            // 2. Billing Info
            PdfPTable billingTable = new PdfPTable(1);
            billingTable.setWidthPercentage(100);
            PdfPCell billToCell = new PdfPCell();
            billToCell.setBorder(Rectangle.NO_BORDER);
            billToCell.addElement(new Paragraph("BILL TO:", HEADER_FONT));
            billToCell.addElement(new Paragraph(invoice.getBillingName(), BOLD_FONT));
            if (invoice.getBillingAddress() != null) {
                billToCell.addElement(new Paragraph(invoice.getBillingAddress(), NORMAL_FONT));
            }
            String billingEmail = invoice.getBillingEmail() != null ? invoice.getBillingEmail() : invoice.getUserId().getEmail();
            billToCell.addElement(new Paragraph(billingEmail, NORMAL_FONT));
            billingTable.addCell(billToCell);
            document.add(billingTable);
            document.add(new Paragraph("\n"));

            // 3. Line Items Table
            PdfPTable itemsTable = new PdfPTable(new float[]{3, 1, 1, 1});
            itemsTable.setWidthPercentage(100);
            
            addTableHeader(itemsTable, "Description");
            addTableHeader(itemsTable, "Net Amount");
            addTableHeader(itemsTable, "VAT Amount");
            addTableHeader(itemsTable, "Total");

            itemsTable.addCell(new Phrase(getInvoiceDescription(invoice), NORMAL_FONT));
            itemsTable.addCell(new Phrase(formatAmount(invoice.getNetAmount(), invoice.getCurrency()), NORMAL_FONT));
            itemsTable.addCell(new Phrase(formatAmount(invoice.getVatAmount(), invoice.getCurrency()), NORMAL_FONT));
            itemsTable.addCell(new Phrase(formatAmount(invoice.getGrossAmount(), invoice.getCurrency()), NORMAL_FONT));

            document.add(itemsTable);
            document.add(new Paragraph("\n"));

            // 4. Totals & VAT Breakdown
            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(40);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

            addTotalRow(totalsTable, "Subtotal (Net):", formatAmount(invoice.getNetAmount(), invoice.getCurrency()));
            addTotalRow(totalsTable, "VAT:", formatAmount(invoice.getVatAmount(), invoice.getCurrency()));
            addTotalRow(totalsTable, "Total Gross:", formatAmount(invoice.getGrossAmount(), invoice.getCurrency()));

            document.add(totalsTable);

            document.close();
        } catch (DocumentException e) {
            throw new IOException(e);
        }

        return out.toByteArray();
    }

    public byte[] generateCreatorEarningsInvoicePdf(com.joinlivora.backend.payout.CreatorEarningsInvoice invoice) throws IOException {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Header: Platform Company Details (from stored data)
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);

            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);

            String sellerName = invoice.getSellerName() != null ? invoice.getSellerName() : companyProperties.getName();
            String sellerAddress = invoice.getSellerAddress() != null ? invoice.getSellerAddress() : companyProperties.getAddress();
            String sellerVat = invoice.getSellerVatNumber() != null ? invoice.getSellerVatNumber() : companyProperties.getVatNumber();

            leftCell.addElement(new Paragraph(sellerName, TITLE_FONT));
            leftCell.addElement(new Paragraph(sellerAddress, NORMAL_FONT));
            leftCell.addElement(new Paragraph("VAT: " + sellerVat, NORMAL_FONT));

            String sellerEmail = invoice.getSellerEmail() != null ? invoice.getSellerEmail() : companyProperties.getEmail();
            leftCell.addElement(new Paragraph(sellerEmail, NORMAL_FONT));
            headerTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph p = new Paragraph("EARNINGS INVOICE", TITLE_FONT);
            p.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(p);
            Paragraph p2 = new Paragraph("Number: " + invoice.getInvoiceNumber(), NORMAL_FONT);
            p2.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(p2);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneOffset.UTC);
            Paragraph p3 = new Paragraph("Date: " + formatter.format(invoice.getCreatedAt()), NORMAL_FONT);
            p3.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(p3);
            headerTable.addCell(rightCell);

            document.add(headerTable);
            document.add(new Paragraph("\n"));

            // 2. Creator Info
            PdfPTable creatorTable = new PdfPTable(1);
            creatorTable.setWidthPercentage(100);
            PdfPCell creatorCell = new PdfPCell();
            creatorCell.setBorder(Rectangle.NO_BORDER);
            creatorCell.addElement(new Paragraph("PAY TO:", HEADER_FONT));
            creatorCell.addElement(new Paragraph(invoice.getCreatorName(), BOLD_FONT));
            if (invoice.getCreatorAddress() != null) {
                creatorCell.addElement(new Paragraph(invoice.getCreatorAddress(), NORMAL_FONT));
            }
            creatorCell.addElement(new Paragraph(invoice.getCreatorEmail(), NORMAL_FONT));
            creatorTable.addCell(creatorCell);
            document.add(creatorTable);
            document.add(new Paragraph("\n"));

            // 3. Period Info
            Paragraph period = new Paragraph("Earnings Period: " + 
                    formatter.format(invoice.getPeriodStart()) + " to " + 
                    formatter.format(invoice.getPeriodEnd()), NORMAL_FONT);
            document.add(period);
            document.add(new Paragraph("\n"));

            // 4. Summary Table
            PdfPTable summaryTable = new PdfPTable(new float[]{3, 1});
            summaryTable.setWidthPercentage(100);

            addTableHeader(summaryTable, "Description");
            addTableHeader(summaryTable, "Amount");

            summaryTable.addCell(new Phrase("Gross Earnings", NORMAL_FONT));
            summaryTable.addCell(new Phrase(formatAmount(invoice.getGrossEarnings(), invoice.getCurrency()), NORMAL_FONT));

            summaryTable.addCell(new Phrase("Platform Fee", NORMAL_FONT));
            summaryTable.addCell(new Phrase("-" + formatAmount(invoice.getPlatformFee(), invoice.getCurrency()), NORMAL_FONT));

            summaryTable.addCell(new Phrase("Net Payout", BOLD_FONT));
            summaryTable.addCell(new Phrase(formatAmount(invoice.getNetEarnings(), invoice.getCurrency()), BOLD_FONT));

            document.add(summaryTable);

            document.close();
        } catch (DocumentException e) {
            throw new IOException(e);
        }

        return out.toByteArray();
    }

    private void addTableHeader(PdfPTable table, String headerTitle) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        header.setBorderWidth(1);
        header.setPhrase(new Phrase(headerTitle, HEADER_FONT));
        table.addCell(header);
    }

    private void addTotalRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, NORMAL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, BOLD_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String getInvoiceDescription(Invoice invoice) {
        return switch (invoice.getInvoiceType()) {
            case SUBSCRIPTION -> "Premium Subscription";
            case PPV -> "Pay-Per-View Content Access";
            case TOKENS -> "Token Pack Purchase";
            case TIPS -> "Creator Tip / Highlighted Message";
            default -> "Livora Platform Service";
        };
    }

    private String formatAmount(java.math.BigDecimal amount, String currencyCode) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        try {
            format.setCurrency(Currency.getInstance(currencyCode.toUpperCase()));
        } catch (Exception e) {
            // Fallback
        }
        return format.format(amount);
    }
}
