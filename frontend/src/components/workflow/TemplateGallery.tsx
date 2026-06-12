import { useState, useMemo } from 'react';
import {
  Box,
  Card,
  CardContent,
  CardActions,
  Button,
  Typography,
  Chip,
  TextField,
  Grid,
  InputAdornment,
  Stack,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import WaterIcon from '@mui/icons-material/Water';
import AirIcon from '@mui/icons-material/Air';
import BuildIcon from '@mui/icons-material/Build';
import BarChartIcon from '@mui/icons-material/BarChart';
import ReportIcon from '@mui/icons-material/Report';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import type { SvgIconComponent } from '@mui/icons-material';
import type { WorkflowTemplate } from '@/types/workflowTemplate';
import { WORKFLOW_TEMPLATES } from '@/data/workflowTemplates';

// ── Icon lookup map (avoids dynamic import) ──────────────────────────────────
const ICON_MAP: Record<string, SvgIconComponent> = {
  Water: WaterIcon,
  Air: AirIcon,
  Build: BuildIcon,
  BarChart: BarChartIcon,
  Report: ReportIcon,
};

// ── Category metadata ─────────────────────────────────────────────────────────
type Category = WorkflowTemplate['category'];

const CATEGORY_LABELS: Record<Category, string> = {
  FLOOD: 'Lũ lụt',
  AIR_QUALITY: 'Chất lượng không khí',
  EQUIPMENT: 'Thiết bị',
  ESG: 'ESG',
  COMPLAINT: 'Khiếu nại',
};

const CATEGORY_COLORS: Record<Category, 'primary' | 'info' | 'warning' | 'success' | 'error'> = {
  FLOOD: 'primary',
  AIR_QUALITY: 'info',
  EQUIPMENT: 'warning',
  ESG: 'success',
  COMPLAINT: 'error',
};

const ALL_CATEGORIES: Category[] = ['FLOOD', 'AIR_QUALITY', 'EQUIPMENT', 'ESG', 'COMPLAINT'];

// ── Sub-components ────────────────────────────────────────────────────────────

function TemplateIcon({ name }: { name: string }) {
  const IconComponent = ICON_MAP[name] ?? AutoAwesomeIcon;
  return <IconComponent fontSize="medium" />;
}

function DurationLabel({ minutes }: { minutes: number }) {
  if (minutes < 60) {
    return <Typography variant="caption" color="text.secondary">~{minutes} phút</Typography>;
  }
  const hours = Math.floor(minutes / 60);
  return <Typography variant="caption" color="text.secondary">~{hours} giờ</Typography>;
}

interface TemplateCardProps {
  template: WorkflowTemplate;
  onSelect: (template: WorkflowTemplate) => void;
}

function TemplateCard({ template, onSelect }: TemplateCardProps) {
  const chipColor = CATEGORY_COLORS[template.category];

  return (
    <Card
      variant="outlined"
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        transition: 'box-shadow 0.2s',
        '&:hover': { boxShadow: 3 },
      }}
    >
      <CardContent sx={{ flexGrow: 1 }}>
        <Box display="flex" alignItems="flex-start" gap={1.5} mb={1.5}>
          <Box
            sx={{
              p: 0.75,
              borderRadius: 1,
              bgcolor: `${chipColor}.main`,
              color: `${chipColor}.contrastText`,
              display: 'flex',
              alignItems: 'center',
              flexShrink: 0,
            }}
          >
            <TemplateIcon name={template.icon} />
          </Box>
          <Box flex={1} minWidth={0}>
            <Typography variant="subtitle2" fontWeight={700} noWrap title={template.name}>
              {template.name}
            </Typography>
            <Chip
              label={CATEGORY_LABELS[template.category]}
              size="small"
              color={chipColor}
              sx={{ mt: 0.25, height: 18, fontSize: 10 }}
            />
          </Box>
        </Box>

        <Typography
          variant="body2"
          color="text.secondary"
          sx={{
            display: '-webkit-box',
            WebkitLineClamp: 3,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            mb: 1.5,
            lineHeight: 1.4,
          }}
        >
          {template.description}
        </Typography>

        <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
          <Typography variant="caption" color="text.secondary">
            {template.params.length} tham số
          </Typography>
          <DurationLabel minutes={template.estimatedDurationMinutes} />
        </Box>

        <Stack direction="row" flexWrap="wrap" gap={0.5}>
          {template.tags.map((tag) => (
            <Chip key={tag} label={tag} size="small" variant="outlined" sx={{ height: 18, fontSize: 10 }} />
          ))}
        </Stack>
      </CardContent>

      <CardActions sx={{ pt: 0, px: 2, pb: 1.5 }}>
        <Button
          variant="contained"
          size="small"
          fullWidth
          onClick={() => onSelect(template)}
          aria-label={`Dùng template ${template.name}`}
        >
          Dùng Template
        </Button>
      </CardActions>
    </Card>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

interface TemplateGalleryProps {
  onSelectTemplate: (template: WorkflowTemplate) => void;
}

export default function TemplateGallery({ onSelectTemplate }: TemplateGalleryProps) {
  const [searchText, setSearchText] = useState('');
  const [activeCategory, setActiveCategory] = useState<Category | null>(null);

  const filtered = useMemo(() => {
    const query = searchText.trim().toLowerCase();
    return WORKFLOW_TEMPLATES.filter((t) => {
      const matchesCategory = activeCategory === null || t.category === activeCategory;
      if (!matchesCategory) return false;
      if (!query) return true;
      return (
        t.name.toLowerCase().includes(query) ||
        t.description.toLowerCase().includes(query) ||
        t.tags.some((tag) => tag.toLowerCase().includes(query))
      );
    });
  }, [searchText, activeCategory]);

  return (
    <Box sx={{ mt: 2 }}>
      {/* Search + Category filter */}
      <Box display="flex" gap={2} mb={2} flexWrap="wrap" alignItems="center">
        <TextField
          size="small"
          placeholder="Tìm kiếm template..."
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          sx={{ minWidth: 240 }}
          inputProps={{ 'aria-label': 'Tìm kiếm workflow template' }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />

        <Stack direction="row" flexWrap="wrap" gap={0.75}>
          <Chip
            label="Tất cả"
            size="small"
            onClick={() => setActiveCategory(null)}
            color={activeCategory === null ? 'primary' : 'default'}
            variant={activeCategory === null ? 'filled' : 'outlined'}
            aria-label="Lọc tất cả danh mục"
            sx={{ cursor: 'pointer' }}
          />
          {ALL_CATEGORIES.map((cat) => (
            <Chip
              key={cat}
              label={CATEGORY_LABELS[cat]}
              size="small"
              onClick={() => setActiveCategory(cat === activeCategory ? null : cat)}
              color={activeCategory === cat ? CATEGORY_COLORS[cat] : 'default'}
              variant={activeCategory === cat ? 'filled' : 'outlined'}
              aria-label={`Lọc danh mục ${CATEGORY_LABELS[cat]}`}
              sx={{ cursor: 'pointer' }}
            />
          ))}
        </Stack>
      </Box>

      {/* Results count */}
      <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>
        {filtered.length} template{filtered.length !== 1 ? 's' : ''}
      </Typography>

      {/* Empty state */}
      {filtered.length === 0 && (
        <Box
          display="flex"
          flexDirection="column"
          alignItems="center"
          justifyContent="center"
          py={6}
          gap={1}
        >
          <AutoAwesomeIcon sx={{ fontSize: 48, color: 'text.disabled' }} />
          <Typography color="text.secondary" variant="body2">
            Không tìm thấy template phù hợp
          </Typography>
        </Box>
      )}

      {/* Grid */}
      <Grid container spacing={2}>
        {filtered.map((template) => (
          <Grid item xs={12} sm={6} md={4} lg={3} key={template.id}>
            <TemplateCard template={template} onSelect={onSelectTemplate} />
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
