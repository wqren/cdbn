package edu.umich.eecs545.w12;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

/**
 * TODO: handle inputs that are larger than memory by loading them only as needed
 * @author Augie
 */
public class Train {

    public static void main(String[] args) throws Exception {
        // Expected args
        if (args.length != 8) {
            throw new Exception("Expected 8 arguments: [directory with training PCD files] [input dimensions] [cdbn model output file] [point cloud segment dimension size (point cloud is square)] [hidden layer dimension size] [pooling layer dimension size parameter] [unit group size] [svm model output file]");
        }
        int argCount = 0;

        // Access the training file directory
        File trainingFileDirectory = new File(args[argCount++]);
        // Check the directory
        if (!trainingFileDirectory.exists()) {
            throw new IOException("Directory containing trianing files does not exist.");
        }
        if (!trainingFileDirectory.isDirectory()) {
            throw new IOException("Expecting a directory (not a file) containing training files.");
        }

        // Get input dimension
        int inputDimensions = Integer.valueOf(args[argCount++]);
        if (inputDimensions < 2) {
            throw new Exception("Number of point cloud dimensions must be at least 2.");
        }
        
        // Access the C-DBN model output file
        File outputFile = new File(args[argCount++]);
        // Check if exists
        if (outputFile.exists()) {
            throw new IOException("Output file already exists.");
        }
        // Make parent directory if doesn't exist
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            throw new IOException("Could not create output file  parent directory.");
        }

        // Get N_V
        int N_V = Integer.valueOf(args[argCount++]);
        if (N_V <= 0) {
            throw new Exception("Point cloud dimension size must be greater than zero.");
        }

        // Get N_H
        int N_H = Integer.valueOf(args[argCount++]);
        if (N_H <= 0) {
            throw new Exception("Hidden layer dimension size must be greater than zero.");
        }

        // Get C
        int C = Integer.valueOf(args[argCount++]);
        if (C <= 0) {
            throw new Exception("Pooling layer dimension size must be greater than zero.");
        }
        
        // Get K
        int K = Integer.valueOf(args[argCount++]);
        if (K <= 0) {
            throw new Exception("Group unit size must be greater than zero.");
        }

        // Access the SVM model output file
        File outputSVMFile = new File(args[argCount++]);
        // Check if exists
        if (outputSVMFile.exists()) {
            throw new IOException("SVM output file already exists.");
        }
        // Make parent directory if doesn't exist
        if (!outputSVMFile.getParentFile().exists() && !outputSVMFile.getParentFile().mkdirs()) {
            throw new IOException("Could not create SVM output file parent directory.");
        }

        // Get the files from the directory
        List<String> trainingFileLocations = new LinkedList<String>();
        for (String fileLocation : trainingFileDirectory.list()) {
            // Only using PCD files
            if (!fileLocation.toLowerCase().endsWith(".pcd")) {
                continue;
            }
            trainingFileLocations.add(fileLocation);
        }

        // Check number of trianing files
        if (trainingFileLocations.isEmpty()) {
            throw new Exception("Did not find any training files in the given directory.");
        }

        // Convert the training data into RGBD point cloud segment objects
        List<Input> segments = new LinkedList<Input>();
        for (String trainingFileLocation : trainingFileLocations) {
            // Access the file
            File trainingFile = new File(trainingFileLocation);
            // Expecting files
            if (trainingFile.isDirectory()) {
                throw new Exception("Expecting training files to be files, not directories.");
            }
            // Read in the point cloud from the given file
            segments.add(new Input(trainingFile));
        }

        // Train the CDBN
        CDBN cdbn = new CDBN(inputDimensions, K, N_V, N_H, C);
        cdbn.train(segments);

        // Write the learned CDBN to disk
        cdbn.write(outputFile);

        // Format the training data for SVM classification using the CDBN pooling layer(s) activations as features
        svm_problem problem = new svm_problem();
        // l appears to be the number of training samples
        // TODO: confirm
        problem.l = segments.size();
        // Get the activiation features from the CDBN
        {
            ArrayList<svm_node[]> svmTrainingSamples = new ArrayList<svm_node[]>();
            for (Input segment : segments) {
                svmTrainingSamples.add(cdbn.getSVMFeatures(segment));
            }
            problem.x = svmTrainingSamples.toArray(new svm_node[0][0]);
        }
        // y is probably the labels
        // TODO: confirm
        {
            double[] labels = new double[segments.size()];
            Set<Integer> labelSet = new HashSet<Integer>();
            for (Input segment : segments) {
                labelSet.add(segment.label);
            }
            problem.y = labels;
        }

        // Set the training parameters
        svm_parameter param = new svm_parameter();
        // Type
        // 0 -- C-SVC (classification)
	// 1 -- nu-SVC (classification)
	// 2 -- one-class SVM
	// 3 -- epsilon-SVR (regression)
	// 4 -- nu-SVR (regression)
        param.svm_type = 0;
        // Other C-SVC parameters:
        // param.weight[] : sets the parameter C of class i to weight*c (default 1)
        // param.C : cost, set the parameter C of C-SVC (default 1)

        // Train the SVM classifier
        svm_model svmModel = svm.svm_train(problem, param);

        // Save the trained SVM classifier
        LibSVMUtils.write(svmModel, outputSVMFile);
    }
}