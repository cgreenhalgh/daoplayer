# DaoPlayer Log Plugin design / implementation notes

Goal: allow wordpress to be used as a frontend for running daoplayer log processing scripts (coffeescript) and viewing resulting visualisations (html/javascript).

## strategy

Use WordPress authentication.

Use WordPress file handling where possible, e.g. uploaded logs files, compositions.

Bundle tools files with plugin.

Option(s) for specifying node executable.

## design

A custom post type, `daoplog`. One instance = one set of files/visualisations.

Custom metabox allowing specific files to be uploaded for the instance:
- log file
- context json composition file
- track (sections) json composition file(s)

? zip file handling
? gz file handling

Links to URLs for generated visualisations.

Button to trigger log re-processing. (Also tracked as attachments)

## useful links

- (file upload in a meta box)[http://wordpress.stackexchange.com/questions/4307/how-can-i-add-an-image-upload-field-directly-to-a-custom-write-panel/4413#4413]
- (upload quota per user plugin)[https://plugins.trac.wordpress.org/browser/upload-quota-per-user/trunk/upload-quota-per-user.php]
- (additional mime types for upload)[http://www.wpbeginner.com/wp-tutorials/how-to-add-additional-file-types-to-be-uploaded-in-wordpress/]

