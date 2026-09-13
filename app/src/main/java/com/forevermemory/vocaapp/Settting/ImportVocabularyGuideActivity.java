package com.forevermemory.vocaapp.Settting;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.forevermemory.vocaapp.R;
import com.google.android.material.button.MaterialButton;

/** Shows the supported spreadsheet format before opening the file picker. */
public class ImportVocabularyGuideActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String[]> excelPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null && !isFinishing()) {
                    ImportVocabularyHelper.handlePickedFile(this, uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_vocabulary_guide);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("엑셀에서 가져오기");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        MaterialButton selectFileButton = findViewById(R.id.selectExcelFileButton);
        selectFileButton.setOnClickListener(v ->
                excelPickerLauncher.launch(ImportVocabularyHelper.mimeTypes()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
