package com.nova.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hasRecentSearches: Boolean = false
) {
    val cornerRadius = 28.dp
    
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(68.dp),
        shape = RoundedCornerShape(cornerRadius),
        color = Color(0xFF1E1E1E)
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxSize(),
            placeholder = {
                Text(
                    text = "Search songs, artists, or albums",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { 
                            onQueryChange("")
                            onActiveChange(false)
                        }
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = Color.White
                        )
                    }
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch() }
            ),
            singleLine = true
        )
    }
} 