package eu.amadeos.apps.writepad.service.print;

import eu.amadeos.apps.writepad.service.layout.PagedSectionView;
import eu.amadeos.commons.print.PrintJobs;

import java.awt.print.PrinterException;

/**
 * Prints a laid-out document through the system's printers (CUPS on amadeOS).
 */
public class PrintService {
    /**
     * Shows the print dialog and prints. The pages are painted by Swing views: call it on the EDT.
     *
     * @return false if the user cancelled the dialog
     */
    public boolean print(PagedSectionView section, String jobName) throws PrinterException {
        return PrintJobs.printWithDialog(new DocumentPageable(section), section.pageLayout().settings().paper(),
                jobName);
    }
}
