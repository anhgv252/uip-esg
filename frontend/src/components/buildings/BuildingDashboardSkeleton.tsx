import { Card, CardContent, Grid, Skeleton } from '@mui/material'

export function BuildingDashboardSkeleton() {
  return (
    <Grid container spacing={2}>
      {[1, 2, 3].map((i) => (
        <Grid item xs={12} md={4} key={i}>
          <Card>
            <CardContent>
              <Skeleton variant="text" width="60%" height={28} />
              <Skeleton variant="text" width="40%" height={20} sx={{ mt: 0.5 }} />
              <Skeleton variant="rectangular" height={120} sx={{ mt: 2, borderRadius: 1 }} />
              <Skeleton variant="text" width="40%" sx={{ mt: 1.5 }} />
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  )
}
