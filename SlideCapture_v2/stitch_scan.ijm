// Fiji macro: stitch a Slide Capture scan. Run in Fiji: Plugins > Macros > Run...
// Copy the whole Scan_... folder (tile_0001.jpg ...) from the phone to your PC first.
dir = getDirectory("Choose the Scan_... folder");
Dialog.create("Scan grid (see folder name: ..._R<rows>xC<cols>)");
Dialog.addNumber("Columns:", 10);
Dialog.addNumber("Rows:", 10);
Dialog.addNumber("Overlap % (estimate):", 15);
Dialog.show();
c = Dialog.getNumber(); r = Dialog.getNumber(); ov = Dialog.getNumber();
run("Grid/Collection stitching", "type=[Grid: snake by rows] order=[Right & Down                ] grid_size_x="+c+" grid_size_y="+r+" tile_overlap="+ov+" first_file_index_i=1 directory=["+dir+"] file_names=tile_{iiii}.jpg output_textfile_name=TileConfiguration.txt fusion_method=[Linear Blending] regression_threshold=0.30 max/avg_displacement_threshold=2.50 absolute_displacement_threshold=3.50 compute_overlap subpixel_accuracy computation_parameters=[Save computation time (but use more RAM)] image_output=[Fuse and display]");
