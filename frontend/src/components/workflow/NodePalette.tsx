import { Box, Typography, Paper, Stack } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import NotificationsIcon from '@mui/icons-material/Notifications';
import FlagIcon from '@mui/icons-material/Flag';
import AccountTreeIcon from '@mui/icons-material/AccountTree';

interface PaletteNode {
  type: string;
  label: string;
  icon: React.ReactNode;
  color: string;
}

const NODES: PaletteNode[] = [
  { type: 'bpmn:StartEvent', label: 'Start Event', icon: <PlayArrowIcon fontSize="small" />, color: '#43a047' },
  { type: 'bpmn:ServiceTask', label: 'Service Task', icon: <AccountTreeIcon fontSize="small" />, color: '#1e88e5' },
  { type: 'ai:AiDecisionTask', label: 'AI Decision', icon: <SmartToyIcon fontSize="small" />, color: '#7b1fa2' },
  { type: 'bpmn:ServiceTask:notification', label: 'Notification', icon: <NotificationsIcon fontSize="small" />, color: '#f57c00' },
  { type: 'bpmn:EndEvent', label: 'End Event', icon: <FlagIcon fontSize="small" />, color: '#c62828' },
];

interface Props {
  onNodeSelect?: (type: string) => void;
}

/**
 * Draggable node palette for the BPMN workflow designer.
 * Shows available node types: Start, Service Task, AI Decision, Notification, End.
 */
export default function NodePalette({ onNodeSelect }: Props) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5, width: 180 }}>
      <Typography variant="caption" fontWeight={700} color="text.secondary" gutterBottom display="block">
        Node Palette
      </Typography>
      <Stack spacing={0.75}>
        {NODES.map((node) => (
          <Box
            key={node.type}
            draggable
            onDragStart={(e) => {
              e.dataTransfer.setData('text/plain', node.type);
              e.dataTransfer.effectAllowed = 'copy';
            }}
            onClick={() => onNodeSelect?.(node.type)}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              p: 0.75,
              borderRadius: 1,
              cursor: 'grab',
              border: '1px solid transparent',
              '&:hover': {
                bgcolor: 'action.hover',
                borderColor: 'divider',
              },
              '&:active': {
                cursor: 'grabbing',
              },
            }}
          >
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 28,
                height: 28,
                borderRadius: '50%',
                border: `1px solid ${node.color}`,
                color: node.color,
                flexShrink: 0,
              }}
            >
              {node.icon}
            </Box>
            <Typography variant="body2" fontSize="0.8rem">
              {node.label}
            </Typography>
          </Box>
        ))}
      </Stack>
    </Paper>
  );
}
