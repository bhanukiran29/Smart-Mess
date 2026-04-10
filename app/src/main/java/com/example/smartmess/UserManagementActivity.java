package com.example.smartmess;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartmess.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserManagementActivity extends AppCompatActivity {

    private RecyclerView rvUsers;
    private TextInputEditText etSearch;
    private TextView tvUserCount;
    private View tvEmpty;
    private ChipGroup cgRoleFilter;
    private CircularProgressIndicator progressBar;
    private FirebaseFirestore db;

    private UserAdapter adapter;
    private final List<User> allUsers     = new ArrayList<>();
    private final List<User> filteredUsers = new ArrayList<>();
    private String activeRoleFilter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        db = FirebaseFirestore.getInstance();

        rvUsers      = findViewById(R.id.rvUsers);
        etSearch     = findViewById(R.id.etSearch);
        tvUserCount  = findViewById(R.id.tvUserCount);
        tvEmpty      = findViewById(R.id.tvEmptyUsers);
        cgRoleFilter = findViewById(R.id.cgRoleFilter);
        progressBar  = findViewById(R.id.progressBarUsers);

        findViewById(R.id.btnUsersBack).setOnClickListener(v -> finish());

        // RecyclerView
        adapter = new UserAdapter(this, filteredUsers, this::showRoleDialog);
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Role filter chips
        cgRoleFilter.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) { activeRoleFilter = "all"; }
            else {
                int id = ids.get(0);
                if (id == R.id.chipAll)     activeRoleFilter = "all";
                else if (id == R.id.chipStudent) activeRoleFilter = "student";
                else if (id == R.id.chipStaff)   activeRoleFilter = "staff";
                else if (id == R.id.chipAdmin)   activeRoleFilter = "admin";
            }
            applyFilters();
        });

        loadUsers();
    }

    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);
        rvUsers.setVisibility(View.GONE);

        db.collection("users").get()
            .addOnSuccessListener(snapshot -> {
                progressBar.setVisibility(View.GONE);
                allUsers.clear();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    User u = doc.toObject(User.class);
                    if (u != null) {
                        u.setUserId(doc.getId());
                        allUsers.add(u);
                    }
                }
                applyFilters();
                rvUsers.setVisibility(View.VISIBLE);
                // Stagger animation on first load
                AnimationUtils.staggerRecyclerView(rvUsers, 60);
            })
            .addOnFailureListener(e -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show();
            });
    }

    private void applyFilters() {
        String query = etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.getDefault()) : "";

        filteredUsers.clear();
        for (User u : allUsers) {
            boolean matchesRole = activeRoleFilter.equals("all")
                    || activeRoleFilter.equalsIgnoreCase(u.getRole());
            boolean matchesSearch = query.isEmpty()
                    || (u.getName() != null && u.getName().toLowerCase(Locale.getDefault()).contains(query))
                    || (u.getEmail() != null && u.getEmail().toLowerCase(Locale.getDefault()).contains(query));
            if (matchesRole && matchesSearch) filteredUsers.add(u);
        }

        int total = filteredUsers.size();
        tvUserCount.setText(total + " user" + (total == 1 ? "" : "s"));
        tvEmpty.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        rvUsers.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void showRoleDialog(User user) {
        String[] roles = {"student", "staff", "admin"};
        String[] labels = {"Student", "Staff", "Admin"};

        int current = 0;
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equalsIgnoreCase(user.getRole())) { current = i; break; }
        }
        final int[] selected = {current};

        new AlertDialog.Builder(this, R.style.AlertDialog_Purple)
            .setTitle("Change role for " + user.getName())
            .setSingleChoiceItems(labels, current, (dialog, which) -> selected[0] = which)
            .setPositiveButton("Save", (dialog, which) -> {
                String newRole = roles[selected[0]];
                db.collection("users").document(user.getUserId())
                    .update("role", newRole)
                    .addOnSuccessListener(a -> {
                        user.setRole(newRole);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this,
                            user.getName() + " is now " + newRole, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                        Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // -------------------------------------------------------------------------
    // RecyclerView Adapter
    // -------------------------------------------------------------------------
    static class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {

        interface OnUserClick { void onClick(User user); }

        private final Context context;
        private final List<User> users;
        private final OnUserClick listener;

        UserAdapter(Context ctx, List<User> users, OnUserClick listener) {
            this.context  = ctx;
            this.users    = users;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context)
                    .inflate(R.layout.item_user_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            User u = users.get(pos);

            // Avatar initials
            String initials = getInitials(u.getName());
            h.tvAvatar.setText(initials);

            h.tvName.setText(u.getName() != null ? u.getName() : "—");
            h.tvEmail.setText(u.getEmail() != null ? u.getEmail() : "—");
            h.tvHostel.setText(u.getHostelBlock() != null && !u.getHostelBlock().isEmpty()
                    ? "Block " + u.getHostelBlock() : "No block");

            // Role badge
            String role = u.getRole() != null ? u.getRole().toLowerCase(Locale.getDefault()) : "student";
            h.tvRoleBadge.setText(capitalize(role));

            switch (role) {
                case "admin":
                    h.tvRoleBadge.setBackgroundResource(R.drawable.badge_admin);
                    h.tvAvatar.setBackgroundResource(R.drawable.avatar_circle_purple);
                    break;
                case "staff":
                    h.tvRoleBadge.setBackgroundResource(R.drawable.badge_staff);
                    h.tvAvatar.setBackgroundResource(R.drawable.avatar_circle_teal);
                    break;
                default:
                    h.tvRoleBadge.setBackgroundResource(R.drawable.badge_student);
                    h.tvAvatar.setBackgroundResource(R.drawable.avatar_circle_blue);
                    break;
            }

            h.btnChangeRole.setOnClickListener(v -> listener.onClick(u));
            h.itemView.setOnLongClickListener(v -> { listener.onClick(u); return true; });
        }

        @Override public int getItemCount() { return users.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvName, tvEmail, tvHostel, tvRoleBadge;
            MaterialButton btnChangeRole;
            VH(@NonNull View v) {
                super(v);
                tvAvatar     = v.findViewById(R.id.tvAvatar);
                tvName       = v.findViewById(R.id.tvUserName);
                tvEmail      = v.findViewById(R.id.tvUserEmail);
                tvHostel     = v.findViewById(R.id.tvUserHostel);
                tvRoleBadge  = v.findViewById(R.id.tvRoleBadge);
                btnChangeRole = v.findViewById(R.id.btnChangeRole);
            }
        }

        private String getInitials(String name) {
            if (name == null || name.isEmpty()) return "?";
            String[] parts = name.trim().split("\\s+");
            if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.getDefault());
            return (parts[0].charAt(0) + "" + parts[1].charAt(0)).toUpperCase(Locale.getDefault());
        }

        private String capitalize(String s) {
            if (s == null || s.isEmpty()) return s;
            return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
        }
    }
}
