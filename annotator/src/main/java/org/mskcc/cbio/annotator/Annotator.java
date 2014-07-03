package org.mskcc.cbio.annotator;

import org.mskcc.cbio.maf.*;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Main class for adding generic annotations.
 * This class depends on some external (non-Java) scripts.
 *
 * @author Selcuk Onur Sumer
 */
public class Annotator
{
	// config params (TODO create a config class instead?)
	protected boolean sortColumns;
	protected boolean addMissingCols;

	// intermediate annotator files
	public static final String INTERMEDIATE_OUT_MAF = "annotator_out.maf";
	public static final String INTERMEDIATE_DIR = "annotator_dir";
	public static final String VEP_PATH = "~/MSKCC/portal/vep/";

	public Annotator()
	{
		// init default settings
		this.sortColumns = false;
		this.addMissingCols = false;
	}

	public void annotateFile(File input,
			File output) throws IOException
	{
		// script to run depends on the extension
		if (input.getName().toLowerCase().endsWith(".vcf"))
		{
			this.runVcf2Maf(input);
		}
		// assuming it is a maf..
		else
		{
			this.runMaf2Maf(input);
		}

		List<String> annoHeaders = this.extractAnnoHeaders(INTERMEDIATE_OUT_MAF);

		FileReader reader = new FileReader(input);
		//FileReader reader = new FileReader(INTERMEDIATE_OUT_MAF);

		BufferedReader bufReader = new BufferedReader(reader);
		MafHeaderUtil headerUtil = new MafHeaderUtil();

		String headerLine = headerUtil.extractHeader(bufReader);
		MafUtil mafUtil = new MafUtil(headerLine);

		AnnoMafProcessor processor = new AnnoMafProcessor(headerLine, annoHeaders);

		FileWriter writer = new FileWriter(output);

		// write comments/metadata to the output
		FileIOUtil.writeLines(writer, headerUtil.getComments());

		// create new header line for output
		List<String> columnNames = processor.newHeaderList(
				this.sortColumns, this.addMissingCols);

		// write the header line to output
		FileIOUtil.writeLine(writer, columnNames);

		String dataLine = bufReader.readLine();
		AnnotatorService service = new AnnotatorService();

		// process the file line by line
		while (dataLine != null)
		{
			// skip empty lines
			if (dataLine.trim().length() == 0)
			{
				dataLine = bufReader.readLine();
				continue;
			}

			// update total number of records processed
			//this.numRecordsProcessed++;

			MafRecord mafRecord = mafUtil.parseRecord(dataLine);
			Map<String, String> annoData = service.annotateRecord(mafRecord);

			// get the data and update/add new oncotator columns
			List<String> data = processor.newDataList(dataLine);

			processor.updateAnnoData(data, annoData);

			// write data to the output file
			FileIOUtil.writeLine(writer, data);

			dataLine = bufReader.readLine();
		}

		reader.close();
		writer.close();

	}

	public void runMaf2Maf(File input) throws IOException
	{
		// TODO enable configuration of hard-coded params
		String inputMaf = input.getAbsolutePath();
		String interDir = INTERMEDIATE_DIR;
		String outMaf = INTERMEDIATE_OUT_MAF;
		String vepPath = VEP_PATH;

		String[] args = {
			"perl",
			"maf2maf.pl",
			"--vep-path",
			vepPath,
			"--input-maf",
			inputMaf,
			"--output-dir",
			interDir,
			"--output-maf",
			outMaf
		};

		execProcess(args);
	}

	public void runVcf2Maf(File input) throws IOException
	{
		// TODO enable configuration of hard-coded params
		String vepPath = VEP_PATH;
		String inVcf = input.getAbsolutePath();
		String outMaf = INTERMEDIATE_OUT_MAF;

		String[] args = {
			"perl",
			"vcf2maf.pl",
			"--vep-path",
			vepPath,
			"--input-vcf",
			inVcf,
			"--output-maf",
			outMaf
		};

		execProcess(args);
	}

	// TODO code duplication! -- we have the same code in liftover module
	/**
	 * Executes an external process via system call.
	 *
	 * @param args          process arguments (including the process itself)
	 * @return              exit value of the process
	 * @throws IOException  if an IO error occurs
	 */
	public static int execProcess(String[] args) throws IOException
	{
		Process process = Runtime.getRuntime().exec(args);

		InputStream stdin = process.getInputStream();
		InputStream stderr = process.getErrorStream();
		InputStreamReader isr = new InputStreamReader(stdin);
		InputStreamReader esr = new InputStreamReader(stderr);
		BufferedReader inReader = new BufferedReader(isr);
		BufferedReader errReader = new BufferedReader(esr);

		// echo output messages to stdout
		String line = null;

		while ((line = inReader.readLine()) != null)
		{
			System.out.println(line);
		}

		// also echo error messages
		while ((line = errReader.readLine()) != null)
		{
			System.out.println(line);
		}

		int exitValue = -1;

		// wait for process to complete
		try
		{
			exitValue = process.waitFor();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}

		return exitValue;
	}

	protected void outputFileNames(File input, File output)
	{
		System.out.println("Reading input from: " + input.getAbsolutePath());
		System.out.println("Writing output to: " + output.getAbsolutePath());
	}

	protected List<String> extractAnnoHeaders(String input) throws IOException
	{
		FileReader reader = new FileReader(input);

		BufferedReader bufReader = new BufferedReader(reader);
		MafHeaderUtil headerUtil = new MafHeaderUtil();

		String headerLine = headerUtil.extractHeader(bufReader);
		String parts[] = headerLine.split("\t");

		reader.close();

		return Arrays.asList(parts);
	}

	// Getters and Setters

	public boolean isSortColumns()
	{
		return sortColumns;
	}

	public void setSortColumns(boolean sortColumns)
	{
		this.sortColumns = sortColumns;
	}

	public boolean isAddMissingCols()
	{
		return addMissingCols;
	}

	public void setAddMissingCols(boolean addMissingCols)
	{
		this.addMissingCols = addMissingCols;
	}
}
