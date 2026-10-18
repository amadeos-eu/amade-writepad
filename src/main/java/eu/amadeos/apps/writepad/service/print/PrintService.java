package eu.amadeos.apps.writepad.service.print;

import eu.amadeos.apps.writepad.domain.DocumentSettings;
import eu.amadeos.apps.writepad.service.layout.PagedSectionView;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/**
 * Prints a laid-out document through the system's printers (CUPS on amadeOS).
 */
public class PrintService {
    /**
     * Shows the print dialog and prints.
     *
     * @return false if the user cancelled the dialog
     */
    public boolean print(PagedSectionView section, String jobName) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(jobName);
        job.setPageable(new DocumentPageable(section));
        PrintRequestAttributeSet attributes = attributes(section.pageLayout().settings(), jobName);
        if (!job.printDialog(attributes)) return false;
        job.print(attributes);
        return true;
    }

    /**
     * The paper for the printer. The orientation is deliberately not requested: the page format of
     * {@link DocumentPageable} already turns landscape pages, and asking the printer as well would turn them
     * twice on some drivers.
     */
    static PrintRequestAttributeSet attributes(DocumentSettings settings, String jobName) {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(settings.paper().media());
        attributes.add(new JobName(jobName, null));
        return attributes;
    }
}
